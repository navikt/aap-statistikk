package no.nav.aap.statistikk

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.statistikk.hendelser.api.MottaStatistikkDTO
import no.nav.aap.statistikk.hendelser.api.mottaStatistikk
import no.nav.aap.statistikk.bigquery.IBigQueryClient
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.Flyway
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.vilkårsresultat.api.vilkårsResultat
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import org.slf4j.LoggerFactory
import java.util.*


val log = LoggerFactory.getLogger("no.nav.aap.statistikk")

class App


fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }

    val dbConfig = DbConfig.fraMiljøVariabler()

    val flyway = Flyway()
    val dataSource = flyway.createAndMigrateDataSource(dbConfig)

    val hendelsesRepository = HendelsesRepository(dataSource)

    // Dummy-implementasjon
    val bigQueryClient = object : IBigQueryClient, IObserver<MottaStatistikkDTO> {
        override fun createIfNotExists(name: String): Boolean {
            log.info("Lager...")
            return true
        }

        override fun insertString(tableName: String, value: String) {
            log.info("Setter inn $value i tabell: $tableName.")
        }

        override fun read(table: String): MutableList<String> {
            log.info("Dummy-les")
            return mutableListOf()
        }

        override fun update(data: MottaStatistikkDTO) {
            insertString("my_table", data.toString())
        }
    }

    hendelsesRepository.registerObserver(bigQueryClient)

    val vilkårsResultatService = VilkårsResultatService(dataSource)
    embeddedServer(Netty, port = 8080) {
        module(hendelsesRepository, vilkårsResultatService)
    }.start(wait = true)
}

fun Application.module(hendelsesRepository: IHendelsesRepository, vilkårsResultatService: VilkårsResultatService) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            LoggerFactory.getLogger(App::class.java).error("Noe gikk galt. %", cause)
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    install(ContentNegotiation) {
        // TODO sjekk om bør gjøre samme settings som behandlingsflyt
        jackson {
            registerModule(JavaTimeModule())
        }
    }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        callIdMdc("callId")
        disableDefaultColors()
        filter { call -> call.request.path().startsWith("/actuator").not() }
    }

    install(OpenAPIGen) {
        // this serves OpenAPI definition on /openapi.json
        serveOpenApiJson = true
        // this servers Swagger UI on /swagger-ui/index.html
        serveSwaggerUi = true
        info {
            title = "AAP - Statistikk"
        }
    }

    routing {
        apiRouting {
            mottaStatistikk(hendelsesRepository)
            vilkårsResultat(vilkårsResultatService)
        }
        route("/") {
            get {
                call.respondText("Hello World!", contentType = ContentType.Text.Plain)
            }
        }
        route("/actuator") {
            get("/metrics") {
                call.respond(prometheus.scrape())
            }
            get("/live") {
                // TODO: logic here
                // https://www.reddit.com/r/kubernetes/comments/wayj42/what_should_readiness_liveness_probe_actually/
                call.respond(HttpStatusCode.OK, "live")
            }
            get("/ready") {
                // TODO: logic here
                call.respond(HttpStatusCode.OK, "ready")
            }
        }
    }

}

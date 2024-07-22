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
import no.nav.aap.statistikk.avsluttetbehandling.api.avsluttetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.bigquery.*
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.Flyway
import no.nav.aap.statistikk.hendelser.api.mottaStatistikk
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.vilkårsresultat.api.vilkårsResultat
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import org.slf4j.LoggerFactory
import java.util.*


private val log = LoggerFactory.getLogger("no.nav.aap.statistikk")

class App

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        log.error("Uhåndtert feil", e)
    }
    val dbConfig = DbConfig.fraMiljøVariabler()
    val bgConfig = BigQueryConfigFromEnv()

    embeddedServer(Netty, port = 8080) {
        startUp(dbConfig, bgConfig)
    }.start(wait = true)
}

fun Application.startUp(dbConfig: DbConfig, bqConfig: BigQueryConfig) {
    val flyway = Flyway(dbConfig)
    val dataSource = flyway.createAndMigrateDataSource()

    environment.monitor.subscribe(ApplicationStopped) {
        log.info("Received shutdown event. Closing Hikari connection pool.")
        dataSource.close()
        environment.monitor.unsubscribe(ApplicationStopped) {}
    }

    val hendelsesRepository = HendelsesRepository(dataSource)

    val bqClient = BigQueryClient(bqConfig)
    val bqRepository = BQRepository(bqClient)
    val bqObserver = BigQueryObserver(bqRepository)
    val vilkårsResultatService = VilkårsResultatService(dataSource)
    vilkårsResultatService.registerObserver(bqObserver)

    val avsluttetBehandlingService = AvsluttetBehandlingService(vilkårsResultatService)

    module(hendelsesRepository, vilkårsResultatService, avsluttetBehandlingService)
}

fun Application.module(
    hendelsesRepository: IHendelsesRepository,
    vilkårsResultatService: VilkårsResultatService,
    avsluttetBehandlingService: AvsluttetBehandlingService
) {
    monitoring()
    statusPages()
    tracing()
    contentNegotation()
    swaggerDoc()

    routing {
        apiRouting {
            mottaStatistikk(hendelsesRepository)
            vilkårsResultat(vilkårsResultatService)
            avsluttetBehandling(avsluttetBehandlingService)
        }
        route("/") {
            get {
                call.respondText("Hello World!", contentType = ContentType.Text.Plain)
            }
        }
    }

}

private fun Application.swaggerDoc() {
    install(OpenAPIGen) {
        // this serves OpenAPI definition on /openapi.json
        serveOpenApiJson = true
        // this serves Swagger UI on /swagger-ui/index.html
        serveSwaggerUi = true
        info {
            title = "AAP - Statistikk"
        }
    }
}

private fun Application.contentNegotation() {
    install(ContentNegotiation) {
        // TODO sjekk om bør gjøre samme settings som behandlingsflyt
        jackson {
            registerModule(JavaTimeModule())
        }
    }
}

private fun Application.tracing() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        callIdMdc("callId")
        disableDefaultColors()
        filter { call -> call.request.path().startsWith("/actuator").not() }
    }
}

private fun Application.monitoring() {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }
    routing {
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
                // Finn ut hvordan disse to kan konfigureres til å gi forskjellig resultat
                call.respond(HttpStatusCode.OK, "ready")
            }
        }
    }
}

private fun Application.statusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            LoggerFactory.getLogger(App::class.java).error("Noe gikk galt.", cause)
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
}

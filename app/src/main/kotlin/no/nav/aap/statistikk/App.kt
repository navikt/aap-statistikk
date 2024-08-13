package no.nav.aap.statistikk

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
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
import no.nav.aap.statistikk.beregningsgrunnlag.BeregningsGrunnlagService
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.BigQueryConfigFromEnv
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.Flyway
import no.nav.aap.statistikk.hendelser.api.mottaStatistikk
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.server.authenticate.AZURE
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.server.authenticate.authentication
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseService
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.VilkårsResultatService
import no.nav.aap.sttistikk.apiRoute
import no.nav.aap.sttistikk.generateOpenAPI
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
    val azureConfig = AzureConfig.fraMiljøVariabler()

    embeddedServer(Netty, port = 8080) {
        startUp(dbConfig, bgConfig, azureConfig)
    }.start(wait = true)
}

fun Application.startUp(dbConfig: DbConfig, bqConfig: BigQueryConfig, azureConfig: AzureConfig) {
    log.info("Starter.")
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
    val vilkårsResultatService = VilkårsResultatService(dataSource, bqRepository)

    val tilkjentYtelseRepository = TilkjentYtelseRepository(dataSource)
    val tilkjentYtelseService = TilkjentYtelseService(tilkjentYtelseRepository, bqRepository)
    val beregningsgrunnlagRepository = BeregningsgrunnlagRepository(dataSource)
    val beregningsGrunnlagService = BeregningsGrunnlagService(beregningsgrunnlagRepository)
    val avsluttetBehandlingService =
        AvsluttetBehandlingService(vilkårsResultatService, tilkjentYtelseService, beregningsGrunnlagService)

    module(hendelsesRepository, avsluttetBehandlingService, azureConfig)
}

fun Application.module(
    hendelsesRepository: IHendelsesRepository,
    avsluttetBehandlingService: AvsluttetBehandlingService,
    azureConfig: AzureConfig
) {
    monitoring()
    statusPages()
    tracing()
    contentNegotation()
    generateOpenAPI()

    authentication(azureConfig)

    routing {
        authenticate(AZURE) {
            apiRoute {
                mottaStatistikk(hendelsesRepository)
                avsluttetBehandling(avsluttetBehandlingService)
            }
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

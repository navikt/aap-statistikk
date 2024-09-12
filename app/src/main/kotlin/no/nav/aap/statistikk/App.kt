package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.Motor
import no.nav.aap.motor.mdc.JobbLogInfoProvider
import no.nav.aap.motor.mdc.LogInformasjon
import no.nav.aap.statistikk.avsluttetbehandling.api.avsluttetBehandling
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.BigQueryConfigFromEnv
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.db.Flyway
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.hendelser.api.mottaStatistikk
import no.nav.aap.statistikk.jobber.JobbAppender
import no.nav.aap.statistikk.jobber.LagreAvsluttertBehandlingJobb
import no.nav.aap.statistikk.jobber.LagreAvsluttetHendelseDTOJobb
import no.nav.aap.statistikk.jobber.LagreHendelseJobb
import no.nav.aap.statistikk.jobber.MotorJobbAppender
import no.nav.aap.statistikk.server.authenticate.AZURE
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.server.authenticate.authentication
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

    val bqClient = BigQueryClient(bqConfig)
    val bqRepository = BQRepository(bqClient)

    val motor = Motor(
        dataSource = dataSource,
        antallKammer = 8,
        logInfoProvider = object : JobbLogInfoProvider {
            override fun hentInformasjon(
                connection: DBConnection,
                jobbInput: JobbInput
            ): LogInformasjon? {
                return LogInformasjon(mapOf())
            }
        },
        jobber = listOf(
            LagreHendelseJobb, LagreAvsluttertBehandlingJobb(bqRepository),
            LagreAvsluttetHendelseDTOJobb
        )
    )

    environment.monitor.subscribe(ApplicationStopped) {
        log.info("Received shutdown event. Closing Hikari connection pool.")
        motor.stop()
        dataSource.close()
        environment.monitor.unsubscribe(ApplicationStopped) {}
    }

    val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

    module(
        transactionExecutor,
        motor,
        MotorJobbAppender(dataSource),
        azureConfig
    )
}

fun Application.module(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    jobbAppender: JobbAppender,
    azureConfig: AzureConfig
) {
    motor.start()

    monitoring()
    statusPages()
    tracing()
    contentNegotation()

    generateOpenAPI()

    authentication(azureConfig)

    routing {
        authenticate(AZURE) {
            apiRoute {
                mottaStatistikk(
                    transactionExecutor,
                    jobbAppender,
                )
                avsluttetBehandling(jobbAppender)
            }
        }
    }
}

private fun Application.contentNegotation() {
    install(ContentNegotiation) {
        // TODO sjekk om bør gjøre samme settings som behandlingsflyt

        jackson {
            registerModule(JavaTimeModule())
            setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            jacksonObjectMapper()
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
            LoggerFactory.getLogger(App::class.java).error(
                "Noe gikk galt. Exception-type: ${cause.javaClass} Query string: ${call.request.queryString()}",
                cause
            )
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
}

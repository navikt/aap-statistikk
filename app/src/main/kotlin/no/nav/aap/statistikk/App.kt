package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.server.authenticate.AZURE
import no.nav.aap.komponenter.commonKtorModule
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
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
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingDTOJobb
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingJobbKonstruktør
import no.nav.aap.statistikk.jobber.LagreHendelseJobb
import no.nav.aap.statistikk.jobber.MotorJobbAppender
import no.nav.aap.statistikk.server.authenticate.azureconfigFraMiljøVariabler
import org.slf4j.LoggerFactory


private val log = LoggerFactory.getLogger("no.nav.aap.statistikk")

class App

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        log.error("Uhåndtert feil", e)
    }
    val dbConfig = DbConfig.fraMiljøVariabler()
    val bgConfig = BigQueryConfigFromEnv()
    val azureConfig = azureconfigFraMiljøVariabler()

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

    val lagreAvsluttetBehandlingJobbKonstruktør = LagreAvsluttetBehandlingDTOJobb(
        jobb = LagreAvsluttetBehandlingJobbKonstruktør(bqRepository)
    )
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
            LagreHendelseJobb, LagreAvsluttetBehandlingJobbKonstruktør(bqRepository),
            lagreAvsluttetBehandlingJobbKonstruktør
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
        MotorJobbAppender(dataSource), lagreAvsluttetBehandlingJobbKonstruktør,
        azureConfig
    )
}

fun Application.module(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    jobbAppender: JobbAppender,
    lagreAvsluttetBehandlingJobb: LagreAvsluttetBehandlingDTOJobb,
    azureConfig: AzureConfig
) {
    motor.start()

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    monitoring(prometheus)
    statusPages()

    commonKtorModule(prometheus, azureConfig, "AAP - Statistikk")

    routing {
        authenticate(AZURE) {
            apiRoute {
                mottaStatistikk(
                    transactionExecutor,
                    jobbAppender,
                )
                avsluttetBehandling(jobbAppender, lagreAvsluttetBehandlingJobb)
            }
        }
    }
}

private fun Application.monitoring(prometheus: PrometheusMeterRegistry) {
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

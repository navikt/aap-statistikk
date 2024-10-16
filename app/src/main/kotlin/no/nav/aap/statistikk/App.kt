package no.nav.aap.statistikk

import com.papsign.ktor.openapigen.model.info.ContactModel
import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
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
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.Motor
import no.nav.aap.motor.api.motorApi
import no.nav.aap.motor.mdc.JobbLogInfoProvider
import no.nav.aap.motor.mdc.LogInformasjon
import no.nav.aap.statistikk.avsluttetbehandling.api.avsluttetBehandling
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfigFromEnv
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.db.Flyway
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.hendelser.api.mottaStatistikk
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingJobbKonstruktør
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.oversikt.oversiktRoute
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.server.authenticate.azureconfigFraMiljøVariabler
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
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
    val bigQueryClient = BigQueryClient(bgConfig, schemaRegistry)

    embeddedServer(Netty, port = 8080) {
        startUp(dbConfig, azureConfig, bigQueryClient)
    }.start(wait = true)
}

fun Application.startUp(
    dbConfig: DbConfig, azureConfig: AzureConfig, bigQueryClient: BigQueryClient
) {
    log.info("Starter.")

    val prometheusMeterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT
    )

    val flyway = Flyway(dbConfig)
    val dataSource = flyway.createAndMigrateDataSource()

    val bqRepository = BQRepository(bigQueryClient)

    val avsluttetBehandlingCounter = prometheusMeterRegistry.avsluttetBehandlingLagret()

    val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
        bqRepository, prometheusMeterRegistry.hendelseLagret(),
        bigQueryKvitteringRepository = { BigQueryKvitteringRepository(it) },
        tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
        beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
        vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
        behandlingRepositoryFactory = { BehandlingRepository(it) }
    )
    val motor = Motor(
        dataSource = dataSource, antallKammer = 8, logInfoProvider = object : JobbLogInfoProvider {
            override fun hentInformasjon(
                connection: DBConnection, jobbInput: JobbInput
            ): LogInformasjon {
                return LogInformasjon(mapOf())
            }
        }, jobber = listOf(
            lagreStoppetHendelseJobb, LagreAvsluttetBehandlingJobbKonstruktør(
                bqRepository, avsluttetBehandlingCounter
            )
        )
    )

    monitor.subscribe(ApplicationStopped) {
        log.info("Received shutdown event. Closing Hikari connection pool.")
        motor.stop()
        dataSource.close()
        monitor.unsubscribe(ApplicationStopped) {}
    }

    val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

    val motorApiCallback: NormalOpenAPIRoute.() -> Unit = {
        motorApi(dataSource)
    }

    module(
        transactionExecutor,
        motor,
        MotorJobbAppender(dataSource),
        azureConfig,
        motorApiCallback,
        lagreStoppetHendelseJobb,
        prometheusMeterRegistry
    )
}

fun Application.module(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    jobbAppender: JobbAppender,
    azureConfig: AzureConfig,
    motorApiCallback: NormalOpenAPIRoute.() -> Unit,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    prometheusMeterRegistry: PrometheusMeterRegistry
) {
    motor.start()

    monitoring(prometheusMeterRegistry)
    statusPages()

    commonKtorModule(
        prometheusMeterRegistry, azureConfig, InfoModel(
            title = "AAP - Statistikk",
            version = "0.0.1",
            description = """
                App med ansvar for å overlevere data for stønadstatistikk og saksstatistikk. For å teste API i dev, besøk
                <a href="https://azure-token-generator.intern.dev.nav.no/api/m2m?aud=dev-gcp:aap:statistikk">Token Generator</a> for å få token.
                """.trimIndent(),
            contact = ContactModel("Slack: #po_aap_dvh"),
        )
    )

    routing {
        authenticate(AZURE) {
            apiRouting {
                mottaStatistikk(
                    transactionExecutor,
                    jobbAppender,
                    lagreStoppetHendelseJobb,
                )
                avsluttetBehandling()
            }
            apiRouting(motorApiCallback)
        }
        oversiktRoute(transactionExecutor)
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

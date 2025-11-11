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
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.Motor
import no.nav.aap.motor.api.motorApi
import no.nav.aap.motor.mdc.JobbLogInfoProvider
import no.nav.aap.motor.mdc.LogInformasjon
import no.nav.aap.motor.retry.RetryService
import no.nav.aap.statistikk.api.*
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.bigquery.BQYtelseRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfigFromEnv
import no.nav.aap.statistikk.bigquery.schemaRegistryYtelseStatistikk
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.db.Migrering
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.kodeverk.kodeverk
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.LagreOppgaveJobb
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobb
import no.nav.aap.statistikk.saksstatistikk.ResendSakstatistikkJobb
import no.nav.aap.statistikk.saksstatistikk.SaksStatistikkService
import no.nav.aap.statistikk.server.authenticate.azureconfigFraMiljøVariabler
import org.slf4j.LoggerFactory
import javax.sql.DataSource


private val log = LoggerFactory.getLogger("no.nav.aap.statistikk")

class App

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        log.error("Uhåndtert feil av type ${e.javaClass}", e)
    }
    val dbConfig = DbConfig.fraMiljøVariabler()
    val bgConfigYtelse = BigQueryConfigFromEnv("ytelsestatistikk")
    val bigQueryClientYtelse = BigQueryClient(bgConfigYtelse, schemaRegistryYtelseStatistikk)

    val azureConfig = azureconfigFraMiljøVariabler()

    val gatewayProvider = defaultGatewayProvider()

    embeddedServer(Netty, port = 8080) {
        startUp(
            dbConfig,
            azureConfig,
            bigQueryClientYtelse,
            gatewayProvider
        )
    }.start(wait = true)
}

fun Application.startUp(
    dbConfig: DbConfig,
    azureConfig: AzureConfig,
    bigQueryClientYtelse: BigQueryClient,
    gatewayProvider: GatewayProvider
) {
    log.info("Starter.")

    val flyway = Migrering(dbConfig)
    val dataSource = flyway.createAndMigrateDataSource()

    val bqYtelseRepository = BQYtelseRepository(bigQueryClientYtelse)

    val sakStatistikkService: (DBConnection) -> SaksStatistikkService = {
        SaksStatistikkService.konstruer(
            gatewayProvider,
            postgresRepositoryRegistry.provider(it)
        )
    }
    val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb(sakStatistikkService)

    val lagreAvsluttetBehandlingTilBigQueryJobb =
        LagreAvsluttetBehandlingTilBigQueryJobb(bqYtelseRepository)

    val resendSakstatistikkJobb = ResendSakstatistikkJobb(sakStatistikkService)

    val motorJobbAppender =
        MotorJobbAppender(
            lagreSakinfoTilBigQueryJobb,
            lagreAvsluttetBehandlingTilBigQueryJobb,
            resendSakstatistikkJobb
        )

    val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
        motorJobbAppender, gatewayProvider
    )
    val lagreAvklaringsbehovHendelseJobb = LagreAvklaringsbehovHendelseJobb(motorJobbAppender)

    val lagreOppgaveJobb = LagreOppgaveJobb()

    val lagreOppgaveHendelseJobb =
        LagreOppgaveHendelseJobb(lagreOppgaveJobb)
    val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()

    val motor = motor(
        dataSource,
        listOf(
            lagreStoppetHendelseJobb,
            lagreAvklaringsbehovHendelseJobb,
            lagreOppgaveHendelseJobb,
            lagrePostmottakHendelseJobb,
            lagreSakinfoTilBigQueryJobb,
            lagreAvsluttetBehandlingTilBigQueryJobb,
            lagreOppgaveJobb,
            resendSakstatistikkJobb
        )
    )

    monitor.subscribe(ApplicationStopPreparing) {
        log.info("Received shutdown event. Closing Hikari connection pool.")
        motor.stop()
        dataSource.close()
        monitor.unsubscribe(ApplicationStopPreparing) {}
    }

    val transactionExecutor = FellesKomponentTransactionalExecutor(dataSource)

    val motorApiCallback: NormalOpenAPIRoute.() -> Unit = {
        motorApi(dataSource)
    }

    motor.start()

    module(
        transactionExecutor,
        motorJobbAppender,
        azureConfig,
        motorApiCallback,
        lagreStoppetHendelseJobb,
        lagreOppgaveHendelseJobb,
        lagrePostmottakHendelseJobb,
        lagreAvklaringsbehovHendelseJobb,
    )
}

fun motor(
    dataSource: DataSource,
    jobber: List<Jobb>,
): Motor {
    return Motor(
        dataSource = dataSource, antallKammer = 8,
        logInfoProvider = object : JobbLogInfoProvider {
            override fun hentInformasjon(
                connection: DBConnection, jobbInput: JobbInput
            ): LogInformasjon {
                return LogInformasjon(mapOf())
            }
        },
        jobber = jobber,
        prometheus = PrometheusProvider.prometheus,
    )
}

fun Application.module(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    azureConfig: AzureConfig,
    motorApiCallback: NormalOpenAPIRoute.() -> Unit,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    lagreOppgaveHendelseJobb: LagreOppgaveHendelseJobb,
    lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
    lagreAvklaringsbehovHendelseJobb: LagreAvklaringsbehovHendelseJobb,
) {
    transactionExecutor.withinTransaction {
        RetryService(it).enable()
    }

    monitoring()
    statusPages()
    kodeverk(transactionExecutor)

    commonKtorModule(
        PrometheusProvider.prometheus, azureConfig, InfoModel(
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
                mottaStoppetBehandling(transactionExecutor, jobbAppender, lagreStoppetHendelseJobb)
                mottaOppdatertBehandling(
                    transactionExecutor,
                    jobbAppender,
                    lagreAvklaringsbehovHendelseJobb
                )
                mottaOppgaveOppdatering(transactionExecutor, jobbAppender, lagreOppgaveHendelseJobb)
                mottaPostmottakOppdatering(
                    transactionExecutor,
                    jobbAppender,
                    lagrePostmottakHendelseJobb
                )
                hentBehandlingstidPerDag(transactionExecutor)
                motorApiCallback()
            }
        }
    }
}


private fun Application.monitoring() {
    routing {
        route("/actuator") {
            get("/metrics") {
                val prometheus = PrometheusProvider.prometheus
                if (prometheus is PrometheusMeterRegistry) {
                    call.respond(prometheus.scrape())
                }
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

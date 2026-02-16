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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbSpesifikasjon
import no.nav.aap.motor.Motor
import no.nav.aap.motor.api.motorApi
import no.nav.aap.motor.mdc.JobbLogInfoProvider
import no.nav.aap.motor.mdc.LogInformasjon
import no.nav.aap.motor.retry.RetryService
import no.nav.aap.statistikk.api.*
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.bigquery.*
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
import no.nav.aap.statistikk.server.authenticate.azureconfigFraMiljøVariabler
import org.slf4j.LoggerFactory
import javax.sql.DataSource


private val log = LoggerFactory.getLogger("no.nav.aap.statistikk")

class App

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        log.error("Uhåndtert feil av type ${e.javaClass}", e)
    }
    val dbConfig = DbConfig.fraMiljøVariabler(AppConfig)
    val bgConfigYtelse = BigQueryConfigFromEnv("ytelsestatistikk")
    val bigQueryClientYtelse = BigQueryClient(bgConfigYtelse, schemaRegistryYtelseStatistikk)

    val azureConfig = azureconfigFraMiljøVariabler()

    val gatewayProvider = defaultGatewayProvider()


    embeddedServer(Netty, configure = {
        connectionGroupSize = AppConfig.connectionGroupSize
        workerGroupSize = AppConfig.workerGroupSize
        callGroupSize = AppConfig.callGroupSize

        shutdownGracePeriod =
            AppConfig.shutdownGracePeriod.inWholeMilliseconds
        shutdownTimeout = AppConfig.shutdownTimeout.inWholeMilliseconds

        connector { port = 8080 }
    }) {

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
    bigQueryClientYtelse: IBigQueryClient,
    gatewayProvider: GatewayProvider
) {
    log.info("Starter.")

    val flyway = Migrering(dbConfig)
    val dataSource = flyway.createAndMigrateDataSource()

    val bqYtelseRepository = BQYtelseRepository(bigQueryClientYtelse)

    val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb()

    val lagreAvsluttetBehandlingTilBigQueryJobb =
        LagreAvsluttetBehandlingTilBigQueryJobb(bqYtelseRepository)

    val motorJobbAppender = MotorJobbAppender()

    val lagreStoppetHendelseJobb =
        LagreStoppetHendelseJobb(motorJobbAppender, lagreAvsluttetBehandlingTilBigQueryJobb)
    val lagreAvklaringsbehovHendelseJobb = LagreAvklaringsbehovHendelseJobb(motorJobbAppender)

    val lagreOppgaveJobb = LagreOppgaveJobb()
    val lagreOppgaveHendelseJobb = LagreOppgaveHendelseJobb()
    val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb()
    val resendSakstatistikkJobb = ResendSakstatistikkJobb()

    val motor = motor(
        dataSource,
        gatewayProvider,
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

    monitor.subscribe(ApplicationStarted) {
        log.info("Ktor-hendelse: ApplicationStarted.")
        motor.start()
    }
    monitor.subscribe(ApplicationStopping) { env ->
        log.info("Ktor-hendelse: ApplicationStopping.")
        // ktor sine eventer kjøres synkront, så vi må kjøre dette asynkront for ikke å blokkere nedstengings-sekvensen
        env.launch(Dispatchers.IO) {
            AppConfig.stansArbeidTimeout
        }
    }

    monitor.subscribe(ApplicationStopped) { environment ->
        environment.log.info("Ktor-hendelsE: ApplicationStopped. Ktor har fullført nedstoppingen sin. Eventuelle requester og annet arbeid som ikke ble fullført innen timeout ble avbrutt.")
        try {
            // Helt til slutt, nå som vi har stanset Motor, etc. Lukk database-koblingen.
            dataSource.close()
        } catch (e: Exception) {
            log.info("Exception etter ApplicationStopped: ${e.message}", e)
        }
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
    )
}

fun motor(
    dataSource: DataSource,
    gatewayProvider: GatewayProvider,
    jobber: List<JobbSpesifikasjon>,
): Motor {
    return Motor(
        dataSource = dataSource, antallKammer = AppConfig.ANTALL_WORKERS_FOR_MOTOR,
        logInfoProvider = object : JobbLogInfoProvider {
            override fun hentInformasjon(
                connection: DBConnection, jobbInput: JobbInput
            ): LogInformasjon {
                return LogInformasjon(mapOf())
            }
        },
        jobber = jobber,
        prometheus = PrometheusProvider.prometheus,
        repositoryRegistry = postgresRepositoryRegistry,
        gatewayProvider = gatewayProvider,
    )
}

fun Application.module(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    azureConfig: AzureConfig,
    motorApiCallback: NormalOpenAPIRoute.() -> Unit,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
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
                )
                mottaOppgaveOppdatering(transactionExecutor, jobbAppender)
                mottaPostmottakOppdatering(
                    transactionExecutor,
                    jobbAppender,
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

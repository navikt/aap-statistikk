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
import io.micrometer.core.instrument.MeterRegistry
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
import no.nav.aap.motor.retry.RetryService
import no.nav.aap.statistikk.api.hentBehandlingstidPerDag
import no.nav.aap.statistikk.api.mottaStatistikk
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.DiagnoseRepositoryImpl
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.*
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.FellesKomponentTransactionalExecutor
import no.nav.aap.statistikk.db.Flyway
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.LagreOppgaveJobb
import no.nav.aap.statistikk.pdl.PdlConfig
import no.nav.aap.statistikk.pdl.PdlGraphQLClient
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.server.authenticate.azureconfigFraMiljøVariabler
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.slf4j.LoggerFactory
import javax.sql.DataSource


private val log = LoggerFactory.getLogger("no.nav.aap.statistikk")

class App

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        log.error("Uhåndtert feil", e)
    }
    val dbConfig = DbConfig.fraMiljøVariabler()
    val bgConfigSak = BigQueryConfigFromEnv("saksstatistikk")
    val bgConfigYtelse = BigQueryConfigFromEnv("ytelsestatistikk")
    val bigQueryClientYtelse = BigQueryClient(bgConfigYtelse, schemaRegistryYtelseStatistikk)
    val bigQueryClientSak = BigQueryClient(bgConfigSak, schemaRegistrySakStatistikk)


    val azureConfig = azureconfigFraMiljøVariabler()
    val pdlConfig = PdlConfig(
        url = System.getenv("INTEGRASJON_PDL_URL"),
        scope = System.getenv("INTEGRASJON_PDL_SCOPE")
    )

    embeddedServer(Netty, port = 8080) {
        startUp(dbConfig, azureConfig, bigQueryClientSak, bigQueryClientYtelse, pdlConfig)
    }.start(wait = true)
}

fun Application.startUp(
    dbConfig: DbConfig,
    azureConfig: AzureConfig,
    bigQueryClientSak: BigQueryClient,
    bigQueryClientYtelse: BigQueryClient,
    pdlConfig: PdlConfig
) {
    log.info("Starter.")

    val prometheusMeterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT
    )

    val flyway = Flyway(dbConfig, prometheusMeterRegistry)
    val dataSource = flyway.createAndMigrateDataSource()

    val bqSakRepository = BigQuerySakstatikkRepository(bigQueryClientSak)
    val bqYtelseRepository = BQYtelseRepository(bigQueryClientYtelse)

    val pdlClient = PdlGraphQLClient(
        pdlConfig = pdlConfig, prometheusMeterRegistry
    )
    val skjermingService = SkjermingService(pdlClient)
    val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb(
        bigQueryKvitteringRepository = { BigQueryKvitteringRepository(it) },
        behandlingRepositoryFactory = { BehandlingRepository(it) },
        bqSakstatikk = bqSakRepository,
        skjermingService = skjermingService
    )

    val lagreAvsluttetBehandlingTilBigQueryJobb = LagreAvsluttetBehandlingTilBigQueryJobb(
        behandlingRepositoryFactory = { BehandlingRepository(it) },
        rettighetstypeperiodeRepositoryFactory = { RettighetstypeperiodeRepository(it) },
        diagnoseRepositoryFactory = { DiagnoseRepositoryImpl(it) },
        vilkårsResulatRepositoryFactory = { VilkårsresultatRepository(it) },
        tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
        beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
        bqRepository = bqYtelseRepository
    )

    val motorJobbAppender =
        MotorJobbAppender(lagreSakinfoTilBigQueryJobb, lagreAvsluttetBehandlingTilBigQueryJobb)

    val lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(
        prometheusMeterRegistry,
        tilkjentYtelseRepositoryFactory = { TilkjentYtelseRepository(it) },
        beregningsgrunnlagRepositoryFactory = { BeregningsgrunnlagRepository(it) },
        vilkårsResultatRepositoryFactory = { VilkårsresultatRepository(it) },
        diagnoseRepository = { DiagnoseRepositoryImpl(it) },
        behandlingRepositoryFactory = { BehandlingRepository(it) },
        rettighetstypeperiodeRepository = { RettighetstypeperiodeRepository(it) },
        personService = { PersonService(PersonRepository(it)) },
        skjermingService = skjermingService,
        jobbAppender = motorJobbAppender,
    )

    val lagreOppgaveHendelseJobb =
        LagreOppgaveHendelseJobb(prometheusMeterRegistry, motorJobbAppender)
    val lagrePostmottakHendelseJobb = LagrePostmottakHendelseJobb(prometheusMeterRegistry)
    val motor = motor(
        dataSource,
        lagreStoppetHendelseJobb,
        prometheusMeterRegistry,
        lagreOppgaveHendelseJobb,
        lagrePostmottakHendelseJobb,
        lagreSakinfoTilBigQueryJobb,
        lagreAvsluttetBehandlingTilBigQueryJobb,
        LagreOppgaveJobb(
            jobbAppender = motorJobbAppender
        ),
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

    module(
        transactionExecutor,
        motor,
        motorJobbAppender,
        azureConfig,
        motorApiCallback,
        lagreStoppetHendelseJobb,
        lagreOppgaveHendelseJobb,
        lagrePostmottakHendelseJobb,
        prometheusMeterRegistry
    )
}

private fun motor(
    dataSource: DataSource,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    prometheusMeterRegistry: PrometheusMeterRegistry,
    lagreOppgaveHendelseJobb: LagreOppgaveHendelseJobb,
    lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
    lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb,
    lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb,
    lagreOppgaveJobb: LagreOppgaveJobb
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
        jobber = listOf(
            lagreStoppetHendelseJobb,
            lagreOppgaveHendelseJobb,
            lagreOppgaveJobb,
            lagrePostmottakHendelseJobb,
            lagreSakinfoTilBigQueryJobb,
            lagreAvsluttetBehandlingTilBigQueryJobb,
        ),
        prometheus = prometheusMeterRegistry,
    )
}

fun Application.module(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    jobbAppender: JobbAppender,
    azureConfig: AzureConfig,
    motorApiCallback: NormalOpenAPIRoute.() -> Unit,
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    lagreOppgaveHendelseJobb: LagreOppgaveHendelseJobb,
    lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
    prometheusMeterRegistry: MeterRegistry,
) {
    motor.start()
    transactionExecutor.withinTransaction {
        RetryService(it).enable()
    }

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
                    lagreOppgaveHendelseJobb,
                    lagrePostmottakHendelseJobb
                )
                hentBehandlingstidPerDag(transactionExecutor)
                motorApiCallback()
            }
        }
    }
}


private fun Application.monitoring(prometheus: MeterRegistry) {
    routing {
        route("/actuator") {
            get("/metrics") {
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

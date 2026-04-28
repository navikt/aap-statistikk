package no.nav.aap.statistikk.testutils

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.Motor
import no.nav.aap.motor.testutil.ManuellMotorImpl
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import no.nav.aap.statistikk.saksstatistikk.BQBehandling
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.defaultGatewayProvider
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.motor
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.LagreOppgaveJobb
import no.nav.aap.statistikk.postgresRepositoryRegistry
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.saksstatistikk.LagreSakinfoTilBigQueryJobb
import no.nav.aap.statistikk.saksstatistikk.ResendSakstatistikkJobb
import no.nav.aap.statistikk.testutils.fakes.FakeBQYtelseRepository
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import no.nav.aap.statistikk.tilbakekreving.LagreTilbakekrevingHendelseJobb
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("MotorTestHelpers")

data class TestJobberSetup(
    val lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb,
    val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb,
    val resendSakstatistikkJobb: ResendSakstatistikkJobb,
    val motorJobbAppender: MotorJobbAppender,
)

fun konstruerTestJobber(
    bqYtelseRepository: IBQYtelsesstatistikkRepository = FakeBQYtelseRepository()
): TestJobberSetup {
    val lagreSakinfoTilBigQueryJobb = LagreSakinfoTilBigQueryJobb()
    val lagreAvsluttetBehandlingTilBigQueryJobb =
        LagreAvsluttetBehandlingTilBigQueryJobb(bqYtelseRepository)
    val resendSakstatistikkJobb = ResendSakstatistikkJobb()
    val motorJobbAppender = MotorJobbAppender()
    return TestJobberSetup(
        lagreSakinfoTilBigQueryJobb,
        lagreAvsluttetBehandlingTilBigQueryJobb,
        resendSakstatistikkJobb,
        motorJobbAppender
    )
}

fun konstruerMotor(
    dataSource: DataSource,
    jobbAppender: MotorJobbAppender,
    bqYtelseRepository: IBQYtelsesstatistikkRepository,
    resendSakstatistikkJobb: ResendSakstatistikkJobb,
    lagreAvklaringsbehovHendelseJobb: LagreAvklaringsbehovHendelseJobb,
    lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
    lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb
): Motor {
    val lagreOppgaveJobb = LagreOppgaveJobb()
    val lagreAvsluttetBehandlingTilBigQueryJobb =
        LagreAvsluttetBehandlingTilBigQueryJobb(bqYtelseRepository)
    return motor(
        dataSource = dataSource,
        gatewayProvider = defaultGatewayProvider { register<FakePdlGateway>() },
        jobber = listOf(
            lagreAvsluttetBehandlingTilBigQueryJobb,
            lagreOppgaveJobb,
            resendSakstatistikkJobb,
            lagreAvklaringsbehovHendelseJobb,
            lagrePostmottakHendelseJobb,
            LagreOppgaveHendelseJobb(),
            lagreSakinfoTilBigQueryJobb,
            LagreStoppetHendelseJobb(jobbAppender, lagreAvsluttetBehandlingTilBigQueryJobb),
            LagreTilbakekrevingHendelseJobb()
        )
    )
}

fun konstruerManuellMotor(
    dataSource: DataSource,
    jobbAppender: MotorJobbAppender,
    bqYtelseRepository: IBQYtelsesstatistikkRepository,
    resendSakstatistikkJobb: ResendSakstatistikkJobb,
    lagreAvklaringsbehovHendelseJobb: LagreAvklaringsbehovHendelseJobb,
    lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
    lagreSakinfoTilBigQueryJobb: LagreSakinfoTilBigQueryJobb
): ManuellMotorImpl {
    val lagreOppgaveJobb = LagreOppgaveJobb()
    val lagreAvsluttetBehandlingTilBigQueryJobb =
        LagreAvsluttetBehandlingTilBigQueryJobb(bqYtelseRepository)
    return ManuellMotorImpl(
        dataSource = dataSource,
        jobber = listOf(
            lagreAvsluttetBehandlingTilBigQueryJobb,
            lagreOppgaveJobb,
            resendSakstatistikkJobb,
            lagreAvklaringsbehovHendelseJobb,
            lagrePostmottakHendelseJobb,
            LagreOppgaveHendelseJobb(),
            lagreSakinfoTilBigQueryJobb,
            LagreStoppetHendelseJobb(jobbAppender, lagreAvsluttetBehandlingTilBigQueryJobb),
            LagreTilbakekrevingHendelseJobb()
        ),
        repositoryRegistry = postgresRepositoryRegistry,
        gatewayProvider = defaultGatewayProvider { register<FakePdlGateway>() },
    )
}

val noOpTransactionExecutor = object : TransactionExecutor {
    override fun <E> withinTransaction(block: (DBConnection) -> E): E {
        return block(mockk(relaxed = true))
    }
}

fun motorMock(): Motor {
    val motor = mockk<Motor>()
    every { motor.start() } just Runs
    every { motor.stop() } just Runs
    return motor
}

class MockJobbAppender : JobbAppender {
    var jobber = mutableListOf<JobbInput>()
    var sisteEnhetRetryCount: Int = 0
    var sisteDelayInSeconds: Long = 0
    var sisteStoredBQBehandling: BQBehandling? = null
    var sisteAvklaringsbehovKode: Definisjon? = null

    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        jobber.add(jobb)
    }

    override fun leggTil(
        repositoryProvider: RepositoryProvider,
        jobb: JobbInput
    ) {
        jobber.add(jobb)
    }

    override fun leggTilLagreSakTilBigQueryJobb(
        repositoryProvider: RepositoryProvider,
        behandlingId: BehandlingId,
        delayInSeconds: Long,
        enhetRetryCount: Int,
        triggerKilde: String,
        storedBQBehandling: BQBehandling?,
        avklaringsbehovKode: Definisjon?,
    ) {
        logger.info("NO-OP: skal lagre til BigQuery for behandling $behandlingId. enhetRetryCount=$enhetRetryCount, delay=$delayInSeconds.")
        sisteEnhetRetryCount = enhetRetryCount
        sisteDelayInSeconds = delayInSeconds
        sisteStoredBQBehandling = storedBQBehandling
        sisteAvklaringsbehovKode = avklaringsbehovKode
    }

    override fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        provider: RepositoryProvider,
        behandlingId: BehandlingId,
        lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb
    ) {
        TODO("Not yet implemented")
    }

    override fun leggTilResendSakstatistikkJobb(
        repositoryProvider: RepositoryProvider,
        behandlingId: BehandlingId
    ) {
        TODO("Not yet implemented")
    }
}

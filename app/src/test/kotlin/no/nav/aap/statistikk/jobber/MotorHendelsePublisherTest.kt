package no.nav.aap.statistikk.jobber

import io.mockk.mockk
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorHendelsePublisher
import no.nav.aap.statistikk.jobber.appender.StatistikkHendelse
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MotorHendelsePublisherTest {
    @Test
    fun `utenYtelseJobb kaster UnsupportedOperationException ved YtelsesstatistikkSkalLagres`() {
        val publisher = MotorHendelsePublisher.utenYtelseJobb(
            jobbAppender = mockk<JobbAppender>(),
            repositoryProvider = mockk<RepositoryProvider>(),
        )

        assertThatThrownBy {
            publisher.publiser(StatistikkHendelse.YtelsesstatistikkSkalLagres(BehandlingId(1L)))
        }.isInstanceOf(UnsupportedOperationException::class.java)
    }
}

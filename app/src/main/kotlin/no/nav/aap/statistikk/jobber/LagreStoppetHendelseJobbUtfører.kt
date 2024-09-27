package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.Counter
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.hendelser.HendelsesService
import org.slf4j.LoggerFactory

class LagreStoppetHendelseJobbUtfører(
    private val hendelsesService: HendelsesService,
    private val hendelseLagretCounter: Counter
) : JobbUtfører {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())
        logger.info("Got message: $dto")

        hendelsesService.prosesserNyHendelse(dto)

        hendelseLagretCounter.increment()
    }
}
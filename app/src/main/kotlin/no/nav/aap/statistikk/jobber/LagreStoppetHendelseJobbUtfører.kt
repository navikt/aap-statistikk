package no.nav.aap.statistikk.jobber

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.hendelser.HendelsesService
import org.slf4j.LoggerFactory

class LagreStoppetHendelseJobbUtfører(
    private val hendelsesService: HendelsesService
) : JobbUtfører {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())
        logger.atInfo().setMessage("Got message.").addKeyValue("dto", dto).log()

        hendelsesService.prosesserNyHendelse(dto)
    }
}
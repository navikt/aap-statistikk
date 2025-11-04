package no.nav.aap.statistikk.jobber

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.hendelser.ResendHendelseService

class LagreAvklaringsbehovHendelseJobbUtfører(
    private val resendHendelseService: ResendHendelseService
) :
    JobbUtfører {

    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())
        logger.info("StoppetBehandling mottatt Regenerer statistikk. Behandlingsreferanse: ${dto.behandlingReferanse}.")

        if (!Miljø.erProd()) {
            val hendelseJSON = DefaultJsonMapper.toJson(dto)
            logger.info("Regenerer statistikk for behandling ${dto.behandlingReferanse}. Hendelse: $hendelseJSON")
        }
        resendHendelseService.prosesserNyHistorikkHendelse(dto)
        logger.info("Ferdig å regenerere statistikk for behandling ${dto.behandlingReferanse}.")
    }
}
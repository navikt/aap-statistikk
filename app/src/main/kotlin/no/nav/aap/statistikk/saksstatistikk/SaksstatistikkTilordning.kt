package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.oppgave.Oppgave
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private const val AUTOMATISK_ENHET = "KELVIN_AUTOMATISK"
private val log = LoggerFactory.getLogger(SaksstatistikkTilordning::class.java)

internal data class SaksstatistikkTilordning(val ansvarligEnhetKode: String?, val saksbehandler: String?)

internal fun utledSaksstatistikkTilordning(
    behandling: Behandling,
    snapshots: List<SakstatistikkSnapshot>,
    oppgaver: List<Oppgave>,
    cutoffTidspunkt: LocalDateTime,
): SaksstatistikkTilordning = SaksstatistikkTilordning(
    ansvarligEnhetKode = ansvarligEnhet(behandling, snapshots, oppgaver, cutoffTidspunkt),
    saksbehandler = utledSaksbehandler(behandling, snapshots, oppgaver),
)

private fun utledSaksbehandler(
    behandling: Behandling,
    snapshots: List<SakstatistikkSnapshot>,
    oppgaver: List<Oppgave>,
): String? {
    val saksbehandler = snapshots.lastOrNull()?.saksbehandler

    if (behandling.behandlingStatus() == BehandlingStatus.AVSLUTTET && saksbehandler == null) {
        return behandling.sisteSaksbehandler ?: behandling.sisteSaksbehandlerSomLøstebehov
            ?: oppgaver.maxByOrNull { it.sistEndret() }?.reservasjon?.reservertAv?.ident
    }

    return saksbehandler
}

private fun ansvarligEnhet(
    behandling: Behandling,
    snapshots: List<SakstatistikkSnapshot>,
    oppgaver: List<Oppgave>,
    cutoffTidspunkt: LocalDateTime,
): String? {
    val enhet = snapshots.lastOrNull()?.enhet

    return when {
        behandling.behandlingMetode() == BehandlingMetode.AUTOMATISK -> AUTOMATISK_ENHET

        enhet != null -> enhet

        behandling.behandlingStatus() == BehandlingStatus.IVERKSETTES -> {
            log.info(
                "Fant ikke oppgave for iverksettes-steg. Behandling: ${behandling.referanse}. Sak: ${behandling.sak.saksnummer}.",
            )
            oppgaver.maxByOrNull { it.sistEndret() }?.enhet?.kode
        }

        behandling.behandlingStatus() == BehandlingStatus.AVSLUTTET ->
            oppgaver.maxByOrNull { it.sistEndret() }?.enhet?.kode

        behandling.venteÅrsak != null -> {
            val enhetSistLøsteAvklaringsbehov = oppgaver
                .filter { it.avklaringsbehov == behandling.sisteLøsteAvklaringsbehov?.kode?.name }
                .maxByOrNull { it.sistEndret() }?.enhet?.kode

            enhetSistLøsteAvklaringsbehov ?: oppgaver.maxByOrNull { it.sistEndret() }?.enhet?.kode
        }

        behandling.behandlingStatus() == BehandlingStatus.UTREDES -> {
            val kode = behandling.gjeldendeAvklaringsBehov?.kode?.name
            if (kode == null) {
                historiskEnhetForSistLøsteBehov(behandling, oppgaver, cutoffTidspunkt)
            } else {
                oppgaver
                    .filter { it.avklaringsbehov == kode }
                    .maxByOrNull { it.sistEndret() }?.enhet?.kode
            }
        }

        else -> null
    }
}

private fun historiskEnhetForSistLøsteBehov(
    behandling: Behandling,
    oppgaver: List<Oppgave>,
    cutoffTidspunkt: LocalDateTime,
): String? {
    val kode = behandling.sisteLøsteAvklaringsbehov?.kode?.name ?: return null
    return oppgaver.flatMap { it.hendelser }
        .filter { it.avklaringsbehovKode == kode && it.sendtTid <= cutoffTidspunkt }
        .maxWithOrNull(compareBy({ it.sendtTid }, { it.mottattTidspunkt }))
        ?.enhet
}

package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.BehandlingId
import java.time.LocalDateTime

interface ISaksStatistikkService {
    fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        lagreUtenEnhet: Boolean = false
    ): SakStatistikkResultat

    /**
     * Lagrer sakinfo med den opprinnelige behandlingstilstanden fra [originalHendelsestid],
     * men re-resolver enhet og saksbehandler fra ferske oppgave-data.
     */
    fun lagreMedOppgavedata(
        behandlingId: BehandlingId,
        originalHendelsestid: LocalDateTime,
        lagreUtenEnhet: Boolean = false
    ): SakStatistikkResultat
}
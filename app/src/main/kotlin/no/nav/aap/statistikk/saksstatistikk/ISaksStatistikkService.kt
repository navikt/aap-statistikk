package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.behandling.BehandlingId
import java.time.LocalDateTime

interface ISaksStatistikkService {
    fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
    ): SakStatistikkResultat

    /**
     * Lagrer sakinfo med den opprinnelige behandlingstilstanden fra [originalHendelsestid],
     * men re-resolver enhet og saksbehandler fra ferske oppgave-data.
     */
    fun lagreMedOppgavedata(
        behandlingId: BehandlingId,
        originalHendelsestid: LocalDateTime,
    ): SakStatistikkResultat

    /**
     * Lagrer sakinfo trigget av en spesifikk oppgavehendelse.
     * Bruker [oppgaveSendtTid] som endretTid for å gi hver oppgavehendelse en unik nøkkel,
     * selv om sendtTid er eldre enn siste behandlingsflyt-tidspunkt.
     */
    fun lagreSakInfoMedOppgaveTidspunkt(
        behandlingId: BehandlingId,
        oppgaveSendtTid: LocalDateTime,
    ): SakStatistikkResultat
}
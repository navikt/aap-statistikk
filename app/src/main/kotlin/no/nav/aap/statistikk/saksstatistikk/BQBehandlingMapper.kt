package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.oppgave.Oppgave
import no.nav.aap.statistikk.årsakTilOpprettelseIkkeSatt
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val SKJERMET_ENHET = "-5"

// Team Sak tåler maks 100 tegn for dimensjons-/kodeverksfeltene
// (behandling_status, behandling_type, behandling_resultat, behandlingmetode, behandling_aarsak).
private const val MAKS_LENGDE_KODEVERKSFELT = 100

data class SaksstatistikkGrunnlag(
    val behandling: Behandling,
    val erSkjermet: Boolean,
    val relatertBehandlingUUID: String?,
    val oppgaver: List<Oppgave>,
    val rettighetstyper: List<RettighetstypePeriode>,
    val tekniskTid: LocalDateTime,
)

object BQBehandlingMapper {
    private val log = LoggerFactory.getLogger(javaClass)

    fun bqBehandlingForBehandling(
        grunnlag: SaksstatistikkGrunnlag,
        cutoffTidspunkt: LocalDateTime? = null,
    ): BQBehandling {
        val behandling = grunnlag.behandling
        val erSkjermet = grunnlag.erSkjermet
        val sak = behandling.sak
        val behandlingReferanse = behandling.referanse

        val oppgaver = grunnlag.oppgaver
        val snapshots = SakstatistikkEventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)
            .filter { cutoffTidspunkt == null || it.tidspunkt <= cutoffTidspunkt }

        val sisteSnapshot = snapshots.lastOrNull()
        val oppgaveDriverEndretTid = sisteSnapshot?.kilde == Kilde.Oppgave
        log.info(
            "Siste snapshot av type: ${sisteSnapshot?.kilde}, tidspunkt: ${sisteSnapshot?.tidspunkt}. " +
                "behandling.oppdatertTidspunkt: ${behandling.oppdatertTidspunkt()}. " +
                "Antall oppgaver: ${oppgaver.size}.",
        )
        if (oppgaveDriverEndretTid) {
            val alleSnapshots = snapshots.joinToString(" | ") {
                "${it.kilde}@${it.tidspunkt}(status=${it.status}, avkl=${it.avklaringsbehov}, enhet=${it.enhet})"
            }
            log.info("Oppgave driver endretTid. Alle snapshots: $alleSnapshots")
        }

        val tilordning =
            if (erSkjermet) {
                SaksstatistikkTilordning(ansvarligEnhetKode = SKJERMET_ENHET, saksbehandler = SKJERMET_ENHET)
            } else {
                utledSaksstatistikkTilordning(
                    behandling,
                    snapshots,
                    oppgaver,
                    cutoffTidspunkt ?: behandling.oppdatertTidspunkt(),
                )
            }

        val saksbehandler = tilordning.saksbehandler

        // Advarsel: samme saksbehandler har hatt en annen behandlingmetode tidligere i denne behandlingen
        val behandlingMetode = behandling.behandlingMetode()
        if (!erSkjermet && saksbehandler != null && behandlingMetode == BehandlingMetode.KVALITETSSIKRING) {
            val tidligereBehandlingMetoderForSaksbehandler = snapshots
                .dropLast(1)
                .filter { it.saksbehandler == saksbehandler }
                .mapNotNull { snapshot ->
                    snapshot.avklaringsbehov?.let {
                        avklaringsbehovTilBehandlingMetode(
                            it,
                        )
                    }
                }
                .filter { it != behandlingMetode }
                .toSet()

            if (tidligereBehandlingMetoderForSaksbehandler.isNotEmpty()) {
                log.warn(
                    "Saksbehandler $saksbehandler har hatt behandlingmetode $behandlingMetode, " +
                        "men hadde tidligere $tidligereBehandlingMetoderForSaksbehandler for behandling $behandlingReferanse og saksnummer ${sak.saksnummer}. " +
                        "Mulig feil i saksbehandler-utledning.",
                )
            }
        }

        val årsakTilOpprettelse = behandling.årsakTilOpprettelse
        if (årsakTilOpprettelse == null) {
            log.info("Årsak til opprettelse er ikke satt. Behandling: $behandlingReferanse. Sak: ${sak.saksnummer}.")
            PrometheusProvider.prometheus.årsakTilOpprettelseIkkeSatt().increment()
        }

        val mottattTid = behandling.nåværendeTilstand().mottattTid
        if (mottattTid.isAfter(behandling.opprettetTid)) {
            log.warn(
                "Mottatt-tid er større enn opprettet-tid. Behandling: $behandlingReferanse. Mottatt: $mottattTid, opprettet: ${behandling.opprettetTid}.",
            )
        }

        val bqBehandling = byggBQBehandling(
            behandling = behandling,
            relatertBehandlingUUID = grunnlag.relatertBehandlingUUID,
            erSkjermet = erSkjermet,
            ansvarligEnhet = tilordning.ansvarligEnhetKode,
            saksbehandler = saksbehandler,
            endretTid = snapshots.last().tidspunkt,
            behandlingStatus = behandlingStatus(behandling),
            behandlingMetode = behandlingMetode,
            behandlingResultat = regnUtBehandlingResultat(behandling, grunnlag.rettighetstyper),
            tekniskTid = grunnlag.tekniskTid,
        )

        if (behandlingMetode == BehandlingMetode.AUTOMATISK) {
            log.info(
                "Behandling $behandlingReferanse er automatisk behandlet. Behandlingtype ${behandling.typeBehandling}",
            )
        }

        return bqBehandling
    }

    private fun byggBQBehandling(
        behandling: Behandling,
        relatertBehandlingUUID: String?,
        erSkjermet: Boolean,
        ansvarligEnhet: String?,
        saksbehandler: String?,
        endretTid: LocalDateTime,
        behandlingStatus: String,
        behandlingMetode: BehandlingMetode,
        behandlingResultat: String?,
        tekniskTid: LocalDateTime,
    ): BQBehandling {
        val årsakTilOpprettelse = behandling.årsakTilOpprettelse
        val sak = behandling.sak

        val hendelser = behandling.hendelser
        val tilstand = behandling.nåværendeTilstand()
        val ansvarligBeslutter = behandling.ansvarligBeslutter()
        val behandlingReferanse = behandling.referanse

        val behandlingType = behandling.typeBehandling.toString().uppercase()
        val behandlingÅrsak = årsakTilOpprettelse ?: behandling.årsaker.prioriterÅrsaker().name

        // behandlingStatus er allerede sikret å holde seg innenfor grensen, jf. ÅrsakTilSattPåVent.kortKode.
        // De øvrige feltene har foreløpig ingen tilsvarende kortkode-løsning, så vi sikrer i det minste
        // at vi ikke sender noe BigQuery-mottaket vil avvise.
        kreverMaksLengdeKodeverksfelt("behandling_type", behandlingType)
        kreverMaksLengdeKodeverksfelt("behandling_aarsak", behandlingÅrsak)
        kreverMaksLengdeKodeverksfelt("behandling_resultat", behandlingResultat)
        kreverMaksLengdeKodeverksfelt("behandlingmetode", behandlingMetode.name)

        return BQBehandling(
            behandlingUUID = behandlingReferanse,
            relatertBehandlingUUID = relatertBehandlingUUID,
            relatertFagsystem = if (relatertBehandlingUUID != null) "Kelvin" else null,
            ferdigbehandletTid = hendelser.ferdigBehandletTid(),
            behandlingType = behandlingType,
            aktorId = sak.person.ident.ident,
            saksnummer = sak.saksnummer.value,
            tekniskTid = tekniskTid,
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
            endretTid = endretTid,
            versjon = behandling.versjon(),
            mottattTid = tilstand.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            opprettetAv = behandling.opprettetAv ?: KELVIN,
            ansvarligBeslutter = if (erSkjermet && ansvarligBeslutter != null) SKJERMET_ENHET else ansvarligBeslutter,
            vedtakTid = behandling.vedtakstidspunkt(),
            søknadsFormat = tilstand.søknadsformat,
            saksbehandler = saksbehandler,
            behandlingMetode = behandlingMetode,
            behandlingStatus = behandlingStatus,
            behandlingÅrsak = behandlingÅrsak,
            behandlingResultat = behandlingResultat,
            resultatBegrunnelse = resultatBegrunnelse(hendelser),
            ansvarligEnhetKode = ansvarligEnhet,
            sakYtelse = "AAP",
            erResending = false,
        )
    }

    /**
     * Om behandlingen er returnert fra kvalitetssikrer, skal dette være returårsaken.
     *
     * TODO: begrunnelse ved avslag/innvilgelse
     */
    private fun resultatBegrunnelse(behandlingHendelser: List<BehandlingHendelse>): String? {
        val tilstand = behandlingHendelser.last().tilTilstand()
        if (tilstand.gjeldendeAvklaringsbehovStatus?.returnert() == true) {
            return tilstand.returÅrsak
        }
        return null
    }

    /**
     * For en avsluttet behandling, tolkes dette som rettighetstype.
     */
    private fun regnUtBehandlingResultat(
        behandling: Behandling,
        rettighetstyper: List<RettighetstypePeriode>,
    ): String? = when (behandling.behandlingStatus()) {
        BehandlingStatus.OPPRETTET,
        BehandlingStatus.UTREDES,
        -> null

        BehandlingStatus.IVERKSETTES,
        BehandlingStatus.AVSLUTTET,
        -> {
            val behandlingReferanse = behandling.referanse
            val resultat = behandling.resultatFraSisteHendelse()

            if (resultat == ResultatKode.INNVILGET && rettighetstyper.size > 1) {
                log.info("Mer enn én rettighetstype for behandling $behandlingReferanse. Velger første.")
            }

            behandlingResultat(resultat, rettighetstyper)
        }
    }

    private fun kreverMaksLengdeKodeverksfelt(feltNavn: String, verdi: String?) {
        require(verdi == null || verdi.length < MAKS_LENGDE_KODEVERKSFELT) {
            "$feltNavn er lengre enn $MAKS_LENGDE_KODEVERKSFELT tegn (${verdi?.length}), " +
                "Team Sak tåler ikke dette. Verdi: $verdi"
        }
    }

    private fun avklaringsbehovTilBehandlingMetode(avklaringsbehov: String): BehandlingMetode =
        AvklaringsbehovKode(avklaringsbehov).tilBehandlingMetode()

    internal fun behandlingResultat(resultat: ResultatKode?, rettighetstyper: List<RettighetstypePeriode>): String =
        when (resultat) {
            ResultatKode.INNVILGET -> if (rettighetstyper.isEmpty()) {
                "AAP"
            } else {
                // TODO: her må vi sikkert heller klippe på dato. Denne vil jo vokse over tid?
                val førsteRettighetstype = rettighetstyper.first().rettighetstype.name.uppercase()
                "AAP_$førsteRettighetstype"
            }

            ResultatKode.AVSLAG -> "AVSLAG"

            ResultatKode.TRUKKET -> "TRUKKET"

            ResultatKode.KLAGE_OPPRETTHOLDES -> "KLAGE_OPPRETTHOLDES"

            ResultatKode.KLAGE_OMGJØRES -> "KLAGE_OMGJØRES"

            ResultatKode.KLAGE_DELVIS_OMGJØRES -> "KLAGE_DELVIS_OMGJØRES"

            ResultatKode.KLAGE_AVSLÅTT -> "KLAGE_AVSLÅTT"

            ResultatKode.KLAGE_TRUKKET -> "KLAGE_TRUKKET"

            ResultatKode.AVBRUTT -> "AVBRUTT"

            null -> "UDEFINERT"
        }

    internal fun behandlingStatus(behandling: Behandling): String {
        val tilstand = behandling.nåværendeTilstand()
        val venteÅrsak =
            tilstand.venteÅrsak?.let { "_${ÅrsakTilSattPåVent.kortKodeFor(it).uppercase()}" }.orEmpty()
        val returStatus = tilstand.gjeldendeAvklaringsbehovStatus
            ?.takeIf { it.returnert() }
            ?.let { "_${it.name.uppercase()}" }.orEmpty()

        val behandlingStatus = behandling.behandlingStatus()
        return when (behandlingStatus) {
            BehandlingStatus.OPPRETTET -> "OPPRETTET"

            BehandlingStatus.UTREDES -> "UNDER_BEHANDLING$venteÅrsak$returStatus"

            BehandlingStatus.IVERKSETTES -> "IVERKSETTES"

            BehandlingStatus.AVSLUTTET -> {
                if (behandling.typeBehandling == TypeBehandling.Klage && behandling.resultatFraSisteHendelse()
                        ?.sendesTilKA() == true
                ) {
                    "OVERSENDT_KA"
                } else {
                    "AVSLUTTET"
                }
            }
        }
    }
}

package no.nav.aap.statistikk.behandling

import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import java.time.LocalDateTime

data class BehandlingTilstand(
    val status: BehandlingStatus,
    val versjon: Versjon,
    val mottattTid: LocalDateTime,
    val søknadsformat: SøknadsFormat,
    val ansvarligBeslutter: String? = null,
    val vedtakstidspunkt: LocalDateTime? = null,
    val sisteSaksbehandler: String? = null,
    val resultat: ResultatKode? = null,
    val gjeldendeAvklaringsBehov: AvklaringsbehovKode? = null,
    val gjeldendeAvklaringsbehovStatus: AvklaringsbehovStatus? = null,
    val sisteLøsteAvklaringsbehov: AvklaringsbehovKode? = null,
    val sisteSaksbehandlerSomLøstebehov: String? = null,
    val sistLøsteAvklaringsbehovTidspunkt: LocalDateTime? = null,
    val gjeldendeStegGruppe: Behandlingssteggruppe? = null,
    val venteÅrsak: String? = null,
    val returÅrsak: String? = null,
    val returÅrsakkoblinger: List<ReturÅrsakTilstand> = emptyList(),
    val relatertBehandlingReferanse: String? = null,
)

@JvmInline
value class AvklaringsbehovKode(val verdi: String)

data class ReturÅrsakTilstand(val avklaringsbehov: AvklaringsbehovKode, val returÅrsaker: List<String>)

enum class AvklaringsbehovStatus {
    OPPRETTET,
    AVSLUTTET,
    TOTRINNS_VURDERT,
    SENDT_TILBAKE_FRA_BESLUTTER,
    KVALITETSSIKRET,
    SENDT_TILBAKE_FRA_KVALITETSSIKRER,
    AVBRUTT,
    ;

    fun returnert(): Boolean = when (this) {
        SENDT_TILBAKE_FRA_BESLUTTER, SENDT_TILBAKE_FRA_KVALITETSSIKRER -> true
        OPPRETTET, AVSLUTTET, TOTRINNS_VURDERT, KVALITETSSIKRET, AVBRUTT -> false
    }
}

enum class Behandlingssteggruppe {
    START_BEHANDLING,
    MIGRERING,
    KRAV,
    SEND_FORVALTNINGSMELDING,
    RETTIGHETSPERIODE,
    SØKNAD,
    AVBRYT_REVURDERING,
    ALDER,
    LOVVALG,
    MEDLEMSKAP,
    OPPHOLDSKRAV,
    BARNETILLEGG,
    SYKDOM,
    GRUNNLAG,
    ET_ANNET_STED,
    SAMORDNING,
    VEDTAKSLENGDE,
    UNDERVEIS,
    TILKJENT_YTELSE,
    SIMULERING,
    VEDTAK,
    FATTE_VEDTAK,
    KVALITETSSIKRING,
    IVERKSETT_VEDTAK,
    BREV,
    AVSLAG_11_27,
    UDEFINERT,
    FORMKRAV,
    KLAGEBEHANDLING_KONTOR,
    KLAGEBEHANDLING_NAY,
    OMGJØRING,
    TREKK_KLAGE,
    OPPRETTHOLDELSE,
    SVAR_FRA_ANDREINSTANS,
    IVERKSETT_KONSEKVENS,
    START_OPPFØLGINGSBEHANDLING,
    AVKLAR_OPPPFØLGING,
    AKTIVITETSPLIKT_11_7,
    AKTIVITETSPLIKT_11_9,
    AVBRYT_AKTIVITETSPLIKTBEHANDLING,
    STUDENT,
}

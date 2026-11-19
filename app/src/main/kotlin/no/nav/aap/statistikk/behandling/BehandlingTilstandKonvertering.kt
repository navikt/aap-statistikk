package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe

fun BehandlingHendelse.tilTilstand(): BehandlingTilstand = BehandlingTilstand(
    status = status,
    versjon = versjon,
    mottattTid = mottattTid,
    søknadsformat = søknadsformat,
    ansvarligBeslutter = ansvarligBeslutter,
    vedtakstidspunkt = vedtakstidspunkt,
    sisteSaksbehandler = saksbehandler?.ident,
    resultat = resultat,
    gjeldendeAvklaringsBehov = avklaringsBehov?.tilAvklaringsbehovKode(),
    gjeldendeAvklaringsbehovStatus = avklaringsbehovStatus?.tilDomene(),
    sisteLøsteAvklaringsbehov = sisteLøsteAvklaringsbehov?.tilAvklaringsbehovKode(),
    sisteSaksbehandlerSomLøstebehov = sisteSaksbehandlerSomLøstebehov,
    sistLøsteAvklaringsbehovTidspunkt = sistLøsteAvklaringsbehovTidspunkt,
    gjeldendeStegGruppe = steggruppe?.tilDomene(),
    venteÅrsak = venteÅrsak,
    returÅrsak = returÅrsak,
    returÅrsakkoblinger = returÅrsakkoblinger.map { it.tilTilstand() },
    relatertBehandlingReferanse = relatertBehandlingReferanse,
)

internal fun Definisjon.tilAvklaringsbehovKode(): AvklaringsbehovKode = AvklaringsbehovKode(kode.name)

internal fun Status.tilDomene(): AvklaringsbehovStatus = when (this) {
    Status.OPPRETTET -> AvklaringsbehovStatus.OPPRETTET
    Status.AVSLUTTET -> AvklaringsbehovStatus.AVSLUTTET
    Status.TOTRINNS_VURDERT -> AvklaringsbehovStatus.TOTRINNS_VURDERT
    Status.SENDT_TILBAKE_FRA_BESLUTTER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
    Status.KVALITETSSIKRET -> AvklaringsbehovStatus.KVALITETSSIKRET
    Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER
    Status.AVBRUTT -> AvklaringsbehovStatus.AVBRUTT
}

@Suppress("CyclomaticComplexMethod")
internal fun StegGruppe.tilDomene(): Behandlingssteggruppe = when (this) {
    StegGruppe.START_BEHANDLING -> Behandlingssteggruppe.START_BEHANDLING
    StegGruppe.MIGRERING -> Behandlingssteggruppe.MIGRERING
    StegGruppe.KRAV -> Behandlingssteggruppe.KRAV
    StegGruppe.SEND_FORVALTNINGSMELDING -> Behandlingssteggruppe.SEND_FORVALTNINGSMELDING
    StegGruppe.RETTIGHETSPERIODE -> Behandlingssteggruppe.RETTIGHETSPERIODE
    StegGruppe.SØKNAD -> Behandlingssteggruppe.SØKNAD
    StegGruppe.AVBRYT_REVURDERING -> Behandlingssteggruppe.AVBRYT_REVURDERING
    StegGruppe.ALDER -> Behandlingssteggruppe.ALDER
    StegGruppe.LOVVALG -> Behandlingssteggruppe.LOVVALG
    StegGruppe.MEDLEMSKAP -> Behandlingssteggruppe.MEDLEMSKAP
    StegGruppe.OPPHOLDSKRAV -> Behandlingssteggruppe.OPPHOLDSKRAV
    StegGruppe.BARNETILLEGG -> Behandlingssteggruppe.BARNETILLEGG
    StegGruppe.SYKDOM -> Behandlingssteggruppe.SYKDOM
    StegGruppe.GRUNNLAG -> Behandlingssteggruppe.GRUNNLAG
    StegGruppe.ET_ANNET_STED -> Behandlingssteggruppe.ET_ANNET_STED
    StegGruppe.SAMORDNING -> Behandlingssteggruppe.SAMORDNING
    StegGruppe.VEDTAKSLENGDE -> Behandlingssteggruppe.VEDTAKSLENGDE
    StegGruppe.UNDERVEIS -> Behandlingssteggruppe.UNDERVEIS
    StegGruppe.TILKJENT_YTELSE -> Behandlingssteggruppe.TILKJENT_YTELSE
    StegGruppe.SIMULERING -> Behandlingssteggruppe.SIMULERING
    StegGruppe.VEDTAK -> Behandlingssteggruppe.VEDTAK
    StegGruppe.FATTE_VEDTAK -> Behandlingssteggruppe.FATTE_VEDTAK
    StegGruppe.KVALITETSSIKRING -> Behandlingssteggruppe.KVALITETSSIKRING
    StegGruppe.IVERKSETT_VEDTAK -> Behandlingssteggruppe.IVERKSETT_VEDTAK
    StegGruppe.BREV -> Behandlingssteggruppe.BREV
    StegGruppe.AVSLAG_11_27 -> Behandlingssteggruppe.AVSLAG_11_27
    StegGruppe.UDEFINERT -> Behandlingssteggruppe.UDEFINERT
    StegGruppe.FORMKRAV -> Behandlingssteggruppe.FORMKRAV
    StegGruppe.KLAGEBEHANDLING_KONTOR -> Behandlingssteggruppe.KLAGEBEHANDLING_KONTOR
    StegGruppe.KLAGEBEHANDLING_NAY -> Behandlingssteggruppe.KLAGEBEHANDLING_NAY
    StegGruppe.OMGJØRING -> Behandlingssteggruppe.OMGJØRING
    StegGruppe.TREKK_KLAGE -> Behandlingssteggruppe.TREKK_KLAGE
    StegGruppe.OPPRETTHOLDELSE -> Behandlingssteggruppe.OPPRETTHOLDELSE
    StegGruppe.SVAR_FRA_ANDREINSTANS -> Behandlingssteggruppe.SVAR_FRA_ANDREINSTANS
    StegGruppe.IVERKSETT_KONSEKVENS -> Behandlingssteggruppe.IVERKSETT_KONSEKVENS
    StegGruppe.START_OPPFØLGINGSBEHANDLING -> Behandlingssteggruppe.START_OPPFØLGINGSBEHANDLING
    StegGruppe.AVKLAR_OPPPFØLGING -> Behandlingssteggruppe.AVKLAR_OPPPFØLGING
    StegGruppe.AKTIVITETSPLIKT_11_7 -> Behandlingssteggruppe.AKTIVITETSPLIKT_11_7
    StegGruppe.AKTIVITETSPLIKT_11_9 -> Behandlingssteggruppe.AKTIVITETSPLIKT_11_9
    StegGruppe.AVBRYT_AKTIVITETSPLIKTBEHANDLING -> Behandlingssteggruppe.AVBRYT_AKTIVITETSPLIKTBEHANDLING
    StegGruppe.STUDENT -> Behandlingssteggruppe.STUDENT
}

internal fun ReturÅrsakkobling.tilTilstand(): ReturÅrsakTilstand =
    ReturÅrsakTilstand(avklaringsbehov.tilAvklaringsbehovKode(), returÅrsaker)

// Fjernes sammen med de kontraktavhengige feltene når leserne er migrert.
internal fun AvklaringsbehovKode.tilDefinisjon(): Definisjon = Definisjon.forKode(verdi)

internal fun ReturÅrsakTilstand.tilReturÅrsakkobling(): ReturÅrsakkobling =
    ReturÅrsakkobling(avklaringsbehov.tilDefinisjon(), returÅrsaker)

internal fun AvklaringsbehovStatus.tilKontrakt(): Status = when (this) {
    AvklaringsbehovStatus.OPPRETTET -> Status.OPPRETTET
    AvklaringsbehovStatus.AVSLUTTET -> Status.AVSLUTTET
    AvklaringsbehovStatus.TOTRINNS_VURDERT -> Status.TOTRINNS_VURDERT
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER -> Status.SENDT_TILBAKE_FRA_BESLUTTER
    AvklaringsbehovStatus.KVALITETSSIKRET -> Status.KVALITETSSIKRET
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER -> Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER
    AvklaringsbehovStatus.AVBRUTT -> Status.AVBRUTT
}

@Suppress("CyclomaticComplexMethod")
internal fun Behandlingssteggruppe.tilKontrakt(): StegGruppe = when (this) {
    Behandlingssteggruppe.START_BEHANDLING -> StegGruppe.START_BEHANDLING
    Behandlingssteggruppe.MIGRERING -> StegGruppe.MIGRERING
    Behandlingssteggruppe.KRAV -> StegGruppe.KRAV
    Behandlingssteggruppe.SEND_FORVALTNINGSMELDING -> StegGruppe.SEND_FORVALTNINGSMELDING
    Behandlingssteggruppe.RETTIGHETSPERIODE -> StegGruppe.RETTIGHETSPERIODE
    Behandlingssteggruppe.SØKNAD -> StegGruppe.SØKNAD
    Behandlingssteggruppe.AVBRYT_REVURDERING -> StegGruppe.AVBRYT_REVURDERING
    Behandlingssteggruppe.ALDER -> StegGruppe.ALDER
    Behandlingssteggruppe.LOVVALG -> StegGruppe.LOVVALG
    Behandlingssteggruppe.MEDLEMSKAP -> StegGruppe.MEDLEMSKAP
    Behandlingssteggruppe.OPPHOLDSKRAV -> StegGruppe.OPPHOLDSKRAV
    Behandlingssteggruppe.BARNETILLEGG -> StegGruppe.BARNETILLEGG
    Behandlingssteggruppe.SYKDOM -> StegGruppe.SYKDOM
    Behandlingssteggruppe.GRUNNLAG -> StegGruppe.GRUNNLAG
    Behandlingssteggruppe.ET_ANNET_STED -> StegGruppe.ET_ANNET_STED
    Behandlingssteggruppe.SAMORDNING -> StegGruppe.SAMORDNING
    Behandlingssteggruppe.VEDTAKSLENGDE -> StegGruppe.VEDTAKSLENGDE
    Behandlingssteggruppe.UNDERVEIS -> StegGruppe.UNDERVEIS
    Behandlingssteggruppe.TILKJENT_YTELSE -> StegGruppe.TILKJENT_YTELSE
    Behandlingssteggruppe.SIMULERING -> StegGruppe.SIMULERING
    Behandlingssteggruppe.VEDTAK -> StegGruppe.VEDTAK
    Behandlingssteggruppe.FATTE_VEDTAK -> StegGruppe.FATTE_VEDTAK
    Behandlingssteggruppe.KVALITETSSIKRING -> StegGruppe.KVALITETSSIKRING
    Behandlingssteggruppe.IVERKSETT_VEDTAK -> StegGruppe.IVERKSETT_VEDTAK
    Behandlingssteggruppe.BREV -> StegGruppe.BREV
    Behandlingssteggruppe.AVSLAG_11_27 -> StegGruppe.AVSLAG_11_27
    Behandlingssteggruppe.UDEFINERT -> StegGruppe.UDEFINERT
    Behandlingssteggruppe.FORMKRAV -> StegGruppe.FORMKRAV
    Behandlingssteggruppe.KLAGEBEHANDLING_KONTOR -> StegGruppe.KLAGEBEHANDLING_KONTOR
    Behandlingssteggruppe.KLAGEBEHANDLING_NAY -> StegGruppe.KLAGEBEHANDLING_NAY
    Behandlingssteggruppe.OMGJØRING -> StegGruppe.OMGJØRING
    Behandlingssteggruppe.TREKK_KLAGE -> StegGruppe.TREKK_KLAGE
    Behandlingssteggruppe.OPPRETTHOLDELSE -> StegGruppe.OPPRETTHOLDELSE
    Behandlingssteggruppe.SVAR_FRA_ANDREINSTANS -> StegGruppe.SVAR_FRA_ANDREINSTANS
    Behandlingssteggruppe.IVERKSETT_KONSEKVENS -> StegGruppe.IVERKSETT_KONSEKVENS
    Behandlingssteggruppe.START_OPPFØLGINGSBEHANDLING -> StegGruppe.START_OPPFØLGINGSBEHANDLING
    Behandlingssteggruppe.AVKLAR_OPPPFØLGING -> StegGruppe.AVKLAR_OPPPFØLGING
    Behandlingssteggruppe.AKTIVITETSPLIKT_11_7 -> StegGruppe.AKTIVITETSPLIKT_11_7
    Behandlingssteggruppe.AKTIVITETSPLIKT_11_9 -> StegGruppe.AKTIVITETSPLIKT_11_9
    Behandlingssteggruppe.AVBRYT_AKTIVITETSPLIKTBEHANDLING -> StegGruppe.AVBRYT_AKTIVITETSPLIKTBEHANDLING
    Behandlingssteggruppe.STUDENT -> StegGruppe.STUDENT
}

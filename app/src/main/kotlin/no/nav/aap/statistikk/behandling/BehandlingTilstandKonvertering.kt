package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe

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

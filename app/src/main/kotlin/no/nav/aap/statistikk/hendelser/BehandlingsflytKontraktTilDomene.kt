package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.MeldekortDTO
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Vurderingsbehov.*
import no.nav.aap.statistikk.meldekort.ArbeidIPerioder
import no.nav.aap.statistikk.meldekort.Meldekort
import no.nav.aap.verdityper.dokument.Kanal

internal fun SakStatus.tilDomene(): no.nav.aap.statistikk.sak.SakStatus {
    return when (this) {
        SakStatus.OPPRETTET -> no.nav.aap.statistikk.sak.SakStatus.OPPRETTET
        SakStatus.UTREDES -> no.nav.aap.statistikk.sak.SakStatus.UTREDES
        SakStatus.LØPENDE -> no.nav.aap.statistikk.sak.SakStatus.LØPENDE
        SakStatus.AVSLUTTET -> no.nav.aap.statistikk.sak.SakStatus.AVSLUTTET
    }
}

fun no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.tilDomene(): TypeBehandling =
    when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Førstegangsbehandling -> TypeBehandling.Førstegangsbehandling
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Revurdering -> TypeBehandling.Revurdering
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Tilbakekreving -> TypeBehandling.Tilbakekreving
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Klage -> TypeBehandling.Klage
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.SvarFraAndreinstans -> TypeBehandling.SvarFraAndreinstans
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling -> TypeBehandling.Oppfølgingsbehandling
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt -> TypeBehandling.Aktivitetsplikt
        no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt11_9 -> TypeBehandling.Aktivitetsplikt11_9
    }

fun Status.tilDomene(): BehandlingStatus =
    when (this) {
        Status.OPPRETTET -> BehandlingStatus.OPPRETTET
        Status.UTREDES -> BehandlingStatus.UTREDES
        Status.IVERKSETTES -> BehandlingStatus.IVERKSETTES
        Status.AVSLUTTET -> BehandlingStatus.AVSLUTTET
    }

fun Kanal.tilDomene(): SøknadsFormat {
    return when (this) {
        Kanal.DIGITAL -> SøknadsFormat.DIGITAL
        Kanal.PAPIR -> SøknadsFormat.PAPIR
    }
}


fun List<MeldekortDTO>.tilDomene(): List<Meldekort> {
    return this.map { meldekort ->
        Meldekort(
            journalpostId = meldekort.journalpostId,
            arbeidIPeriodeDTO = meldekort.arbeidIPeriode.map {
                ArbeidIPerioder(
                    periodeFom = it.periodeFom,
                    periodeTom = it.periodeTom,
                    timerArbeidet = it.timerArbeidet
                )
            }
        )
    }
}

@Suppress("CyclomaticComplexMethod")
fun Vurderingsbehov.tilDomene(): no.nav.aap.statistikk.behandling.Vurderingsbehov {
    return when (this) {
        Vurderingsbehov.SØKNAD -> SØKNAD
        Vurderingsbehov.AKTIVITETSMELDING -> AKTIVITETSMELDING
        Vurderingsbehov.MELDEKORT -> MELDEKORT
        Vurderingsbehov.FRITAK_MELDEPLIKT -> MELDEKORT
        Vurderingsbehov.LEGEERKLÆRING -> LEGEERKLÆRING
        Vurderingsbehov.AVVIST_LEGEERKLÆRING -> AVVIST_LEGEERKLÆRING
        Vurderingsbehov.DIALOGMELDING -> DIALOGMELDING
        Vurderingsbehov.G_REGULERING -> G_REGULERING
        Vurderingsbehov.REVURDER_MEDLEMSKAP -> REVURDER_MEDLEMSSKAP
        Vurderingsbehov.REVURDER_YRKESSKADE -> REVURDER_YRKESSKADE
        Vurderingsbehov.REVURDER_BEREGNING -> REVURDER_BEREGNING
        Vurderingsbehov.REVURDER_LOVVALG -> REVURDER_LOVVALG
        Vurderingsbehov.KLAGE -> KLAGE
        Vurderingsbehov.REVURDER_SAMORDNING -> REVURDER_SAMORDNING
        Vurderingsbehov.LOVVALG_OG_MEDLEMSKAP -> LOVVALG_OG_MEDLEMSKAP
        Vurderingsbehov.FORUTGAENDE_MEDLEMSKAP -> FORUTGAENDE_MEDLEMSKAP
        Vurderingsbehov.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND -> SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND
        Vurderingsbehov.BARNETILLEGG -> BARNETILLEGG
        Vurderingsbehov.INSTITUSJONSOPPHOLD -> INSTITUSJONSOPPHOLD
        Vurderingsbehov.SAMORDNING_OG_AVREGNING -> SAMORDNING_OG_AVREGNING
        Vurderingsbehov.REFUSJONSKRAV -> REFUSJONSKRAV
        Vurderingsbehov.UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT -> UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT
        Vurderingsbehov.SØKNAD_TRUKKET -> SØKNAD_TRUKKET
        Vurderingsbehov.VURDER_RETTIGHETSPERIODE -> VURDER_RETTIGHETSPERIODE
        Vurderingsbehov.REVURDER_MANUELL_INNTEKT -> REVURDER_MANUELL_INNTEKT
        Vurderingsbehov.KLAGE_TRUKKET -> KLAGE_TRUKKET
        Vurderingsbehov.MOTTATT_KABAL_HENDELSE -> MOTTATT_KABAL_HENDELSE
        Vurderingsbehov.OPPFØLGINGSOPPGAVE -> OPPFØLGINGSOPPGAVE
        Vurderingsbehov.HELHETLIG_VURDERING -> HELHETLIG_VURDERING
        Vurderingsbehov.REVURDER_MELDEPLIKT_RIMELIG_GRUNN -> REVURDER_MELDEPLIKT_RIMELIG_GRUNN
        Vurderingsbehov.AKTIVITETSPLIKT_11_7 -> AKTIVITETSPLIKT_11_7
        Vurderingsbehov.AKTIVITETSPLIKT_11_9 -> AKTIVITETSPLIKT_11_9
        Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT -> EFFEKTUER_AKTIVITETSPLIKT
        Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT_11_9 -> EFFEKTUER_AKTIVITETSPLIKT_11_9
        Vurderingsbehov.OVERGANG_UFORE -> OVERGANG_UFORE
        Vurderingsbehov.OVERGANG_ARBEID -> OVERGANG_ARBEID
        Vurderingsbehov.REVURDERING_AVBRUTT -> REVURDERING_AVBRUTT
        Vurderingsbehov.DØDSFALL_BRUKER -> DØDSFALL_BRUKER
        Vurderingsbehov.DØDSFALL_BARN -> DØDSFALL_BARN
        Vurderingsbehov.OPPHOLDSKRAV -> OPPHOLDSKRAV
        Vurderingsbehov.REVURDER_STUDENT -> REVURDER_STUDENT
        Vurderingsbehov.REVURDER_SAMORDNING_ANDRE_FOLKETRYGDYTELSER -> REVURDER_SAMORDNING_ANDRE_FOLKETRYGDYTELSER
        Vurderingsbehov.REVURDER_SAMORDNING_UFØRE -> REVURDER_SAMORDNING_UFØRE
        Vurderingsbehov.REVURDER_SAMORDNING_ANDRE_STATLIGE_YTELSER -> REVURDER_SAMORDNING_ANDRE_STATLIGE_YTELSER
        Vurderingsbehov.REVURDER_SAMORDNING_ARBEIDSGIVER -> REVURDER_SAMORDNING_ARBEIDSGIVER
        Vurderingsbehov.REVURDER_SAMORDNING_TJENESTEPENSJON -> REVURDER_SAMORDNING_TJENESTEPENSJON
        Vurderingsbehov.REVURDER_SYKEPENGEERSTATNING -> REVURDER_SYKEPENGEERSTATNING
    }
}
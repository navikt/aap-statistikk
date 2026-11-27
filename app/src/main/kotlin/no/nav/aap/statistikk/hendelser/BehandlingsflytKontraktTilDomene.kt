package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Fritakvurdering
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.MeldekortDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Vurderingsbehov.AKTIVITETSMELDING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.AKTIVITETSPLIKTBEHANDLING_AVBRUTT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.AKTIVITETSPLIKT_11_7
import no.nav.aap.statistikk.behandling.Vurderingsbehov.AKTIVITETSPLIKT_11_9
import no.nav.aap.statistikk.behandling.Vurderingsbehov.AVVIST_LEGEERKLÆRING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.AVVIST_SOKNAD_OM_AAP_UNDER_OPPHOLD_I_UTLANDET
import no.nav.aap.statistikk.behandling.Vurderingsbehov.BARNETILLEGG
import no.nav.aap.statistikk.behandling.Vurderingsbehov.BARNETILLEGG_SATS_REGULERING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.BRUKER_TILBAKE_I_ARBEID
import no.nav.aap.statistikk.behandling.Vurderingsbehov.DIALOGMELDING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.DØDSFALL_BARN
import no.nav.aap.statistikk.behandling.Vurderingsbehov.DØDSFALL_BRUKER
import no.nav.aap.statistikk.behandling.Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.EFFEKTUER_AKTIVITETSPLIKT_11_9
import no.nav.aap.statistikk.behandling.Vurderingsbehov.ETABLERING_EGEN_VIRKSOMHET
import no.nav.aap.statistikk.behandling.Vurderingsbehov.FASTSETT_ARBEIDSEVNE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.FERIE_I_SYKEPENGEPERIODE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.FORUTGAENDE_MEDLEMSKAP
import no.nav.aap.statistikk.behandling.Vurderingsbehov.FRITAK_MELDEPLIKT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.G_REGULERING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.HELHETLIG_VURDERING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.INSTITUSJONSOPPHOLD
import no.nav.aap.statistikk.behandling.Vurderingsbehov.INSTITUSJONSOPPHOLD_HELSEINSTITUSJON
import no.nav.aap.statistikk.behandling.Vurderingsbehov.INSTITUSJONSOPPHOLD_SONING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.KLAGE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.KLAGE_TRUKKET
import no.nav.aap.statistikk.behandling.Vurderingsbehov.LEGEERKLÆRING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.LOVVALG_OG_MEDLEMSKAP
import no.nav.aap.statistikk.behandling.Vurderingsbehov.MELDEKORT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.MIGRERING_FRA_ARENA
import no.nav.aap.statistikk.behandling.Vurderingsbehov.MIGRER_RETTIGHETSPERIODE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.MOTTATT_KABAL_HENDELSE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.OPPFØLGINGSOPPGAVE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.OPPHOLDSKRAV
import no.nav.aap.statistikk.behandling.Vurderingsbehov.OVERGANG_ARBEID
import no.nav.aap.statistikk.behandling.Vurderingsbehov.OVERGANG_UFORE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.OVERGANG_UFORE_AUTOMATISK_STANS
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REFUSJONSKRAV
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDERING_AVBRUTT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_BEREGNING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_INNTEKTSBORTFALL
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_LOVVALG
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_MANUELL_INNTEKT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_MEDLEMSSKAP
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_MELDEPLIKT_RIMELIG_GRUNN
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SAMORDNING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SAMORDNING_ANDRE_FOLKETRYGDYTELSER
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SAMORDNING_ANDRE_STATLIGE_YTELSER
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SAMORDNING_ARBEIDSGIVER
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SAMORDNING_BARNEPENSJON
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SAMORDNING_TJENESTEPENSJON
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SAMORDNING_UFØRE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_STUDENT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SYKEPENGEERSTATNING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_SYKESTIPEND
import no.nav.aap.statistikk.behandling.Vurderingsbehov.REVURDER_YRKESSKADE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.SAMORDNING_OG_AVREGNING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND
import no.nav.aap.statistikk.behandling.Vurderingsbehov.SØKNAD
import no.nav.aap.statistikk.behandling.Vurderingsbehov.SØKNAD_TRUKKET
import no.nav.aap.statistikk.behandling.Vurderingsbehov.UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.UTVID_VEDTAKSLENGDE
import no.nav.aap.statistikk.behandling.Vurderingsbehov.VEDTAKSLENGDE_MANUELT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.VURDER_ARBEIDSOPPTRAPPING
import no.nav.aap.statistikk.behandling.Vurderingsbehov.VURDER_AVSLAG_11_27
import no.nav.aap.statistikk.behandling.Vurderingsbehov.VURDER_FRITAK_MELDEPLIKT
import no.nav.aap.statistikk.behandling.Vurderingsbehov.VURDER_KRAV
import no.nav.aap.statistikk.behandling.Vurderingsbehov.VURDER_RETTIGHETSPERIODE
import no.nav.aap.statistikk.meldekort.ArbeidIPerioder
import no.nav.aap.statistikk.meldekort.Meldekort
import no.nav.aap.verdityper.dokument.Kanal

fun no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.tilDomene(): TypeBehandling = when (this) {
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Førstegangsbehandling -> TypeBehandling.Førstegangsbehandling
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Revurdering -> TypeBehandling.Revurdering
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Tilbakekreving -> TypeBehandling.Tilbakekreving
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Klage -> TypeBehandling.Klage
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.SvarFraAndreinstans -> TypeBehandling.SvarFraAndreinstans
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.OppfølgingsBehandling -> TypeBehandling.Oppfølgingsbehandling
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt -> TypeBehandling.Aktivitetsplikt
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Aktivitetsplikt11_9 -> TypeBehandling.Aktivitetsplikt11_9
    no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling.Utenlandsopphold -> TypeBehandling.Utenlandsopphold
}

fun Status.tilDomene(): BehandlingStatus = when (this) {
    Status.OPPRETTET -> BehandlingStatus.OPPRETTET
    Status.UTREDES -> BehandlingStatus.UTREDES
    Status.IVERKSETTES -> BehandlingStatus.IVERKSETTES
    Status.AVSLUTTET -> BehandlingStatus.AVSLUTTET
}

fun Kanal.tilDomene(): SøknadsFormat = when (this) {
    Kanal.DIGITAL -> SøknadsFormat.DIGITAL
    Kanal.PAPIR -> SøknadsFormat.PAPIR
}

fun List<MeldekortDTO>.tilDomene(): List<Meldekort> = this.map { meldekort ->
    Meldekort(
        journalpostId = meldekort.journalpostId,
        arbeidIPeriodeDTO = meldekort.arbeidIPeriode.map {
            ArbeidIPerioder(
                periodeFom = it.periodeFom,
                periodeTom = it.periodeTom,
                timerArbeidet = it.timerArbeidet,
            )
        },
    )
}

@JvmName("fritakvurderingListTilDomene")
fun List<Fritakvurdering>.tilDomene(): List<no.nav.aap.statistikk.meldekort.Fritakvurdering> = this.map { fritak ->
    no.nav.aap.statistikk.meldekort.Fritakvurdering(
        harFritak = fritak.harFritak,
        fraDato = fritak.fraDato,
        tilDato = fritak.tilDato,
    )
}

@Suppress("CyclomaticComplexMethod")
fun Vurderingsbehov.tilDomene(): no.nav.aap.statistikk.behandling.Vurderingsbehov = when (this) {
    Vurderingsbehov.SØKNAD -> SØKNAD
    Vurderingsbehov.AKTIVITETSMELDING -> AKTIVITETSMELDING
    Vurderingsbehov.MELDEKORT -> MELDEKORT
    Vurderingsbehov.FRITAK_MELDEPLIKT -> FRITAK_MELDEPLIKT
    Vurderingsbehov.VURDER_FRITAK_MELDEPLIKT -> VURDER_FRITAK_MELDEPLIKT
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
    Vurderingsbehov.FASTSETT_ARBEIDSEVNE -> FASTSETT_ARBEIDSEVNE
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
    Vurderingsbehov.BARNETILLEGG_SATS_REGULERING -> BARNETILLEGG_SATS_REGULERING
    Vurderingsbehov.UTVID_VEDTAKSLENGDE -> UTVID_VEDTAKSLENGDE
    Vurderingsbehov.REVURDER_SYKESTIPEND -> REVURDER_SYKESTIPEND
    Vurderingsbehov.MIGRER_RETTIGHETSPERIODE -> MIGRER_RETTIGHETSPERIODE
    Vurderingsbehov.ETABLERING_EGEN_VIRKSOMHET -> ETABLERING_EGEN_VIRKSOMHET
    Vurderingsbehov.REVURDER_SAMORDNING_BARNEPENSJON -> REVURDER_SAMORDNING_BARNEPENSJON
    Vurderingsbehov.VEDTAKSLENGDE_MANUELT -> VEDTAKSLENGDE_MANUELT
    Vurderingsbehov.AKTIVITETSPLIKTBEHANDLING_AVBRUTT -> AKTIVITETSPLIKTBEHANDLING_AVBRUTT
    Vurderingsbehov.OVERGANG_UFORE_AUTOMATISK_STANS -> OVERGANG_UFORE_AUTOMATISK_STANS
    Vurderingsbehov.VURDER_KRAV -> VURDER_KRAV
    Vurderingsbehov.REVURDER_INNTEKTSBORTFALL -> REVURDER_INNTEKTSBORTFALL
    Vurderingsbehov.VURDER_AVSLAG_11_27 -> VURDER_AVSLAG_11_27
    Vurderingsbehov.VURDER_ARBEIDSOPPTRAPPING -> VURDER_ARBEIDSOPPTRAPPING
    Vurderingsbehov.INSTITUSJONSOPPHOLD_SONING -> INSTITUSJONSOPPHOLD_SONING
    Vurderingsbehov.INSTITUSJONSOPPHOLD_HELSEINSTITUSJON -> INSTITUSJONSOPPHOLD_HELSEINSTITUSJON
    Vurderingsbehov.BRUKER_TILBAKE_I_ARBEID -> BRUKER_TILBAKE_I_ARBEID
    Vurderingsbehov.FERIE_I_SYKEPENGEPERIODE -> FERIE_I_SYKEPENGEPERIODE
    Vurderingsbehov.MIGRERING_FRA_ARENA -> MIGRERING_FRA_ARENA
    Vurderingsbehov.AVVIST_SOKNAD_OM_AAP_UNDER_OPPHOLD_I_UTLANDET -> AVVIST_SOKNAD_OM_AAP_UNDER_OPPHOLD_I_UTLANDET
    Vurderingsbehov.SØKNAD_UTENLANDSOPPHOLD -> SØKNAD_UTENLANDSOPPHOLD
}

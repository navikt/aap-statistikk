package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.komponenter.repository.RepositoryRegistry
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.statistikk.avsluttetbehandling.ArbeidsopptrappingperioderRepositoryImpl
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepositoryImpl
import no.nav.aap.statistikk.behandling.BehandlingRepositoryImpl
import no.nav.aap.statistikk.behandling.DiagnoseRepositoryImpl
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepositoryImpl
import no.nav.aap.statistikk.enhet.EnhetRepositoryImpl
import no.nav.aap.statistikk.enhet.SaksbehandlerRepositoryImpl
import no.nav.aap.statistikk.meldekort.FritaksvurderingRepositoryImpl
import no.nav.aap.statistikk.meldekort.MeldekortRepositoryImpl
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepositoryImpl
import no.nav.aap.statistikk.oppgave.OppgaveRepositoryImpl
import no.nav.aap.statistikk.person.PersonRepositoryImpl
import no.nav.aap.statistikk.postmottak.PostmottakBehandlingRepositoryImpl
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepositoryImpl
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepositoryImpl
import no.nav.aap.statistikk.tilbakekreving.TilbakekrevingHendelseRepositoryImpl
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepositoryImpl


val postgresRepositoryRegistry = RepositoryRegistry()
    .register<FlytJobbRepositoryImpl>()
    .register<BehandlingRepositoryImpl>()
    .register<DiagnoseRepositoryImpl>()
    .register<BeregningsgrunnlagRepositoryImpl>()
    .register<PersonRepositoryImpl>()
    .register<SakRepositoryImpl>()
    .register<SakstatistikkRepositoryImpl>()
    .register<PostmottakBehandlingRepositoryImpl>()
    .register<EnhetRepositoryImpl>()
    .register<OppgaveRepositoryImpl>()
    .register<OppgaveHendelseRepositoryImpl>()
    .register<SaksbehandlerRepositoryImpl>()
    .register<RettighetstypeperiodeRepositoryImpl>()
    .register<VilkårsresultatRepositoryImpl>()
    .register<TilkjentYtelseRepositoryImpl>()
    .register<MeldekortRepositoryImpl>()
    .register<ArbeidsopptrappingperioderRepositoryImpl>()
    .register<FritaksvurderingRepositoryImpl>()
    .register<TilbakekrevingHendelseRepositoryImpl>()

fun DBConnection.provider(): RepositoryProvider = postgresRepositoryRegistry.provider(this)
package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.komponenter.repository.RepositoryRegistry
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.statistikk.avsluttetbehandling.ArbeidsopptrappingperioderRepositoryImpl
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepository
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.DiagnoseRepositoryImpl
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.enhet.EnhetRepositoryImpl
import no.nav.aap.statistikk.enhet.SaksbehandlerRepositoryImpl
import no.nav.aap.statistikk.meldekort.FritaksvurderingRepositoryImpl
import no.nav.aap.statistikk.meldekort.MeldekortRepository
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepositoryImpl
import no.nav.aap.statistikk.oppgave.OppgaveRepositoryImpl
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.postmottak.PostmottakBehandlingRepositoryImpl
import no.nav.aap.statistikk.sak.BigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepositoryImpl
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository


val postgresRepositoryRegistry = RepositoryRegistry()
    .register<FlytJobbRepositoryImpl>()
    .register<BehandlingRepository>()
    .register<DiagnoseRepositoryImpl>()
    .register<BeregningsgrunnlagRepository>()
    .register<PersonRepository>()
    .register<SakRepositoryImpl>()
    .register<SakstatistikkRepositoryImpl>()
    .register<PostmottakBehandlingRepositoryImpl>()
    .register<EnhetRepositoryImpl>()
    .register<OppgaveRepositoryImpl>()
    .register<OppgaveHendelseRepositoryImpl>()
    .register<BigQueryKvitteringRepository>()
    .register<SaksbehandlerRepositoryImpl>()
    .register<RettighetstypeperiodeRepository>()
    .register<VilkårsresultatRepository>()
    .register<TilkjentYtelseRepository>()
    .register<MeldekortRepository>()
    .register<ArbeidsopptrappingperioderRepositoryImpl>()
    .register<FritaksvurderingRepositoryImpl>()

fun DBConnection.provider(): RepositoryProvider = postgresRepositoryRegistry.provider(this)
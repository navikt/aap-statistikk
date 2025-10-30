package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.komponenter.repository.RepositoryRegistry
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.DiagnoseRepositoryImpl
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.postmottak.PostmottakBehandlingRepositoryImpl
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepositoryImpl


val postgresRepositoryRegistry = RepositoryRegistry()
    .register<FlytJobbRepositoryImpl>()
    .register<BehandlingRepository>()
    .register<DiagnoseRepositoryImpl>()
    .register<BeregningsgrunnlagRepository>()
    .register<PersonRepository>()
    .register<SakRepositoryImpl>()
    .register<SakstatistikkRepositoryImpl>()
    .register<PostmottakBehandlingRepositoryImpl>()

fun DBConnection.provider(): RepositoryProvider = postgresRepositoryRegistry.provider(this)
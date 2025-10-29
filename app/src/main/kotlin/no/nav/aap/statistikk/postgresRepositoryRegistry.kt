package no.nav.aap.statistikk

import no.nav.aap.komponenter.repository.RepositoryRegistry
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.DiagnoseRepositoryImpl
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsgrunnlagRepository


val postgresRepositoryRegistry = RepositoryRegistry()
    .register<FlytJobbRepositoryImpl>()
    .register<BehandlingRepository>()
    .register<DiagnoseRepositoryImpl>()
    .register<BeregningsgrunnlagRepository>()
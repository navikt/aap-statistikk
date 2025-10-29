package no.nav.aap.statistikk

import no.nav.aap.komponenter.repository.RepositoryRegistry
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.statistikk.behandling.BehandlingRepository


val postgresRepositoryRegistry = RepositoryRegistry()
    .register<FlytJobbRepositoryImpl>()
    .register<BehandlingRepository>()
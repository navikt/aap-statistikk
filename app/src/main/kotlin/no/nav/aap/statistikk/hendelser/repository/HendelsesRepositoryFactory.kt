package no.nav.aap.statistikk.hendelser.repository

import no.nav.aap.komponenter.dbconnect.DBConnection

class HendelsesRepositoryFactory(): Factory<IHendelsesRepository> {
    override fun create(dbConnection: DBConnection): IHendelsesRepository {
        return HendelsesRepository(dbConnection)
    }
}
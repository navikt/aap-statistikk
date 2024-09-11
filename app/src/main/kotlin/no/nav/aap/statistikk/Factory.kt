package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection

interface Factory<out T> {
    fun create(dbConnection: DBConnection): T
}
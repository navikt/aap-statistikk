package no.nav.aap.statistikk.hendelser

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.JobbInput

interface JobbAppender {
    fun leggTil(connection: DBConnection, jobb: JobbInput)
}
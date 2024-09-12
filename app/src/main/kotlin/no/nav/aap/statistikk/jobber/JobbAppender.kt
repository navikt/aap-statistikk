package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.JobbInput

interface JobbAppender {
    fun leggTil(connection: DBConnection, jobb: JobbInput)
    fun leggTil(jobb: JobbInput)
}
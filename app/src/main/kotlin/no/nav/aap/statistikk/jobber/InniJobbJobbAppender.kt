package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput

class InniJobbJobbAppender(private val dbConnection: DBConnection) : JobbAppender {
    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        throw RuntimeException("Kan ikke legge til jobb uten connection for InniJobbJobbAppender.")
    }

    override fun leggTil(jobb: JobbInput) {
        FlytJobbRepository(dbConnection).leggTil(jobb)
    }
}
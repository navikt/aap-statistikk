package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import javax.sql.DataSource

class MotorJobbAppender(private val dataSource: DataSource) : JobbAppender {
    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        FlytJobbRepository(connection).leggTil(jobb)
    }

    override fun leggTil(jobb: JobbInput) {
        dataSource.transaction {
            leggTil(it, jobb)
        }
    }
}
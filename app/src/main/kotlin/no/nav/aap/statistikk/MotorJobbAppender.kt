package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.hendelser.JobbAppender

class MotorJobbAppender : JobbAppender {
    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        FlytJobbRepository(connection).leggTil(jobb)
    }
}
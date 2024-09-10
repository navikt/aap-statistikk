package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører

class LagreAvsluttetBehandlingPostgresJobbUtfører : JobbUtfører {
    override fun utfør(input: JobbInput) {
        TODO("Not yet implemented")
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return LagreAvsluttetBehandlingPostgresJobbUtfører()
        }

        override fun type(): String {
            return "lagreAvsluttetBehandling"
        }

        override fun navn(): String {
            return "Lagre avsluttet behandling"
        }

        override fun beskrivelse(): String {
            return "lagre avsluttet behandling"
        }

    }
}
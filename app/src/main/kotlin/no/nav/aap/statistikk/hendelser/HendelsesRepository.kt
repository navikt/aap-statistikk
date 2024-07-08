package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.IObserver
import no.nav.aap.statistikk.ISubject
import no.nav.aap.statistikk.api.IHendelsesRepository
import no.nav.aap.statistikk.api.MottaStatistikkDTO
import java.sql.Statement
import javax.sql.DataSource

class HendelsesRepository(private val dataSource: DataSource) : IHendelsesRepository, ISubject<MottaStatistikkDTO> {
    private val observers: MutableList<IObserver<MottaStatistikkDTO>> = mutableListOf()

    override fun lagreHendelse(hendelse: MottaStatistikkDTO) {
        // TODO: bedre transaction-håndtering
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO motta_statistikk (saksnummer, status, behandlingstype) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            ).use { s ->
                s.setString(1, hendelse.saksNummer)
                s.setString(2, hendelse.status)
                s.setString(3, hendelse.behandlingsType)
                s.executeUpdate()
            }
        }
    }

    override fun hentHendelser(): Collection<MottaStatistikkDTO> {
        dataSource.connection.use { connection ->
            val rs = connection.prepareStatement(
                "SELECT * FROM motta_statistikk", Statement.RETURN_GENERATED_KEYS
            ).executeQuery()

            val hendelser = mutableListOf<MottaStatistikkDTO>()

            while (rs.next()) {
                val saksNummer = rs.getString("saksnummer")
                val status = rs.getString("status")
                val behandlingType = rs.getString("behandlingstype")

                hendelser.add(MottaStatistikkDTO(saksNummer, status, behandlingType))
            }

            return hendelser
        }
    }

    override fun registerObserver(observer: IObserver<MottaStatistikkDTO>) {
        observers.add(observer)
    }

    override fun removeObserver(observer: IObserver<MottaStatistikkDTO>) {
        observers.remove(observer)
    }

    override fun notifyObservers(data: MottaStatistikkDTO) {
        observers.forEach { o -> o.update(data) }
    }
}
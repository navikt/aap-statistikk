package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory

class PersonRepository(private val dbConnection: DBConnection) : IPersonRepository {
    companion object : RepositoryFactory<IPersonRepository> {
        override fun konstruer(connection: DBConnection): IPersonRepository {
            return PersonRepository(connection)
        }
    }

    override fun lagrePerson(person: Person, identer: Set<Ident>): Long {
        val alleIdenter = identer + person.ident
        låsIdenter(alleIdenter)

        val eksisterendePersonIder = alleIdenter.mapNotNull(::hentEier).distinct()
        require(eksisterendePersonIder.size <= 1) {
            "Identene tilhører allerede flere personer."
        }

        val personId = person.id() ?: eksisterendePersonIder.singleOrNull() ?: opprettPerson(person)
        require(eksisterendePersonIder.all { it == personId }) {
            "En eller flere identer tilhører allerede en annen person."
        }

        synkroniserIdenter(personId, person, alleIdenter)
        return personId
    }

    private fun låsIdenter(identer: Set<Ident>) {
        identer.sortedBy { it.ident }.forEach { ident ->
            dbConnection.queryFirstOrNull("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))") {
                setParams {
                    setString(1, ident.ident)
                }
                setRowMapper { }
            }
        }
    }

    private fun opprettPerson(person: Person): Long {
        return dbConnection.executeReturnKey("INSERT INTO person (skjermet) VALUES (?)") {
            setParams {
                setBoolean(1, person.erSkjermet())
            }
        }
    }

    private fun synkroniserIdenter(personId: Long, person: Person, identer: Set<Ident>) {
        val sorterteIdenter = identer.sortedBy { it.ident }
        val eiere = sorterteIdenter.associateWith(::hentEier)
        val identMedAnnenEier = eiere.entries.firstOrNull { it.value != null && it.value != personId }
        require(identMedAnnenEier == null) {
            "Ident ${identMedAnnenEier?.key} tilhører allerede en annen person " +
                    "(person_id=${identMedAnnenEier?.value})."
        }

        dbConnection.execute("UPDATE person_ident SET aktiv = FALSE WHERE person_id = ? AND aktiv = TRUE") {
            setParams {
                setLong(1, personId)
            }
        }

        sorterteIdenter.forEach { ident ->
            if (eiere[ident] == null) {
                leggTilIdent(personId, ident.ident, ident == person.ident)
            } else {
                oppdaterIdent(personId, ident.ident, ident == person.ident)
            }
        }

        dbConnection.execute("UPDATE person SET skjermet = ? WHERE id = ?") {
            setParams {
                setBoolean(1, person.erSkjermet())
                setLong(2, personId)
            }
        }
    }

    private fun hentEier(ident: Ident): Long? {
        return dbConnection.queryFirstOrNull("SELECT person_id FROM person_ident WHERE ident = ? FOR UPDATE") {
            setParams {
                setString(1, ident.ident)
            }
            setRowMapper {
                it.getLong("person_id")
            }
        }
    }

    private fun leggTilIdent(personId: Long, ident: String, aktiv: Boolean) {
        dbConnection.execute("INSERT INTO person_ident (person_id, ident, aktiv) VALUES (?, ?, ?)") {
            setParams {
                setLong(1, personId)
                setString(2, ident)
                setBoolean(3, aktiv)
            }
        }
    }

    private fun oppdaterIdent(personId: Long, ident: String, aktiv: Boolean) {
        dbConnection.execute("UPDATE person_ident SET aktiv = ? WHERE person_id = ? AND ident = ?") {
            setParams {
                setBoolean(1, aktiv)
                setLong(2, personId)
                setString(3, ident)
            }
        }
    }

    override fun slåSammenPersoner(beholdPersonId: Long, fjernPersonIder: Set<Long>, identer: Set<Ident>) {
        if (fjernPersonIder.isEmpty()) {
            return
        }

        låsIdenter(identer)
        val forventedePersonIder = fjernPersonIder + beholdPersonId
        val faktiskePersonIder = identer.mapNotNull(::hentEier).toSet()
        require(faktiskePersonIder.all { it in forventedePersonIder }) {
            "En eller flere identer tilhører en person som ikke skal slås sammen."
        }

        fjernPersonIder.sorted().forEach { fjernPersonId ->
            flyttPersonreferanser(fjernPersonId, beholdPersonId)
        }
    }

    private fun flyttPersonreferanser(fjernPersonId: Long, beholdPersonId: Long) {
        dbConnection.execute("UPDATE person_ident SET aktiv = FALSE WHERE person_id = ?") {
            setParams {
                setLong(1, fjernPersonId)
            }
        }

        listOf("sak", "relaterte_personer", "postmottak_behandling", "oppgave").forEach { tabell ->
            dbConnection.execute("UPDATE $tabell SET person_id = ? WHERE person_id = ?") {
                setParams {
                    setLong(1, beholdPersonId)
                    setLong(2, fjernPersonId)
                }
            }
        }

        dbConnection.execute("UPDATE person_ident SET person_id = ? WHERE person_id = ?") {
            setParams {
                setLong(1, beholdPersonId)
                setLong(2, fjernPersonId)
            }
        }
        dbConnection.execute("DELETE FROM person WHERE id = ?") {
            setParams {
                setLong(1, fjernPersonId)
            }
        }
    }

    override fun hentPerson(ident: Ident): Person? {
        return dbConnection.queryFirstOrNull(
            """
            SELECT p.id            AS id,
                   p.skjermet      AS skjermet,
                   aktiv_pi.ident  AS aktiv_ident
            FROM person_ident pi
                     JOIN person p ON p.id = pi.person_id
                     JOIN person_ident aktiv_pi ON aktiv_pi.person_id = p.id AND aktiv_pi.aktiv = TRUE
            WHERE pi.ident = ?
            """.trimIndent()
        ) {
            setParams {
                setString(1, ident.ident)
            }
            setRowMapper {
                Person(
                    ident = Ident(it.getString("aktiv_ident")),
                    skjermet = it.getBoolean("skjermet"),
                    id = it.getLong("id"),
                )
            }
        }
    }

}

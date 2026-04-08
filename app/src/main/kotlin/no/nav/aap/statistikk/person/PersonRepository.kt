package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.komponenter.repository.RepositoryFactory

interface PersonRepository : Repository{
    fun lagrePerson(person: Person): Long
    fun hentPerson(ident: String): Person?
}

class PersonRepositoryImpl(private val dbConnection: DBConnection) : PersonRepository {
    companion object : RepositoryFactory<PersonRepository> {
        override fun konstruer(connection: DBConnection): PersonRepository {
            return PersonRepositoryImpl(connection)
        }
    }

    override fun lagrePerson(person: Person): Long {
        return dbConnection.executeReturnKey("INSERT INTO person (ident) VALUES (?)") {
            setParams {
                setString(1, person.ident)
            }
        }
    }

    override fun hentPerson(ident: String): Person? {
        return dbConnection.queryFirstOrNull(
            "SELECT * FROM person WHERE ident = ?"
        ) {
            setParams {
                setString(1, ident)
            }
            setRowMapper {
                Person(
                    ident = it.getString("ident"),
                    id = it.getLong("id"),
                )
            }
        }
    }
}
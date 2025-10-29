package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.komponenter.repository.RepositoryFactory

interface IPersonRepository : Repository{
    fun lagrePerson(person: Person): Long
    fun hentPerson(ident: String): Person?
}

class PersonRepository(private val dbConnection: DBConnection) : IPersonRepository {
    companion object : RepositoryFactory<IPersonRepository> {
        override fun konstruer(connection: DBConnection): IPersonRepository {
            return PersonRepository(connection)
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
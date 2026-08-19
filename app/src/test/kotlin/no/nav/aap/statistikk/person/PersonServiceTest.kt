package no.nav.aap.statistikk.person

import no.nav.aap.statistikk.integrasjoner.pdl.PdlGateway
import no.nav.aap.statistikk.integrasjoner.pdl.PdlIdent
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import no.nav.aap.statistikk.testutils.fakes.FakePersonRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonServiceTest {
    @Test
    fun `oppretter person med gjeldende skjermet-status fra PDL`() {
        val ident = "12345"
        val personService = PersonService(
            FakePersonRepository(),
            FakePdlGateway(identerHemmelig = mapOf(ident to true))
        )

        val person = personService.hentEllerLagrePerson(ident)

        assertThat(person.erSkjermet()).isTrue()
    }

    @Test
    fun `spør PDL på nytt og oppdaterer lagret skjermet-status for eksisterende person`() {
        val ident = "12345"
        val personRepository = FakePersonRepository()

        val identerHemmelig = mutableMapOf(ident to false)
        val pdlGateway = object : PdlGateway {
            override fun hentPersoner(identer: List<String>) =
                FakePdlGateway(identerHemmelig).hentPersoner(identer)

            override fun hentIdenter(ident: String) =
                listOf(PdlIdent(ident = ident, historisk = false))
        }
        val personService = PersonService(personRepository, pdlGateway)

        val førstePerson = personService.hentEllerLagrePerson(ident)
        assertThat(førstePerson.erSkjermet()).isFalse()

        // Personen blir skjermet i mellomtiden.
        identerHemmelig[ident] = true

        val andrePerson = personService.hentEllerLagrePerson(ident)

        assertThat(andrePerson.erSkjermet()).isTrue()
        assertThat(andrePerson.id()).isEqualTo(førstePerson.id())
    }

    @Test
    fun `oppdaterer aktiv ident når PDL returnerer en ny ident`() {
        val gammelIdent = "12345"
        val nyIdent = "67890"
        val personRepository = FakePersonRepository()
        val identerForPerson = mutableMapOf(
            gammelIdent to listOf(PdlIdent(gammelIdent, historisk = false))
        )
        val pdlGateway = FakePdlGateway(identerForPerson = identerForPerson)
        val personService = PersonService(personRepository, pdlGateway)

        val førstePerson = personService.hentEllerLagrePerson(gammelIdent)
        identerForPerson[nyIdent] = listOf(
            PdlIdent(gammelIdent, historisk = true),
            PdlIdent(nyIdent, historisk = false),
        )

        val oppdatertPerson = personService.hentEllerLagrePerson(nyIdent)

        assertThat(oppdatertPerson.id()).isEqualTo(førstePerson.id())
        assertThat(oppdatertPerson.ident).isEqualTo(nyIdent)
        assertThat(personRepository.hentPerson(gammelIdent)?.ident).isEqualTo(nyIdent)
        assertThat(personRepository.hentPerson(nyIdent)?.ident).isEqualTo(nyIdent)
    }

    @Test
    fun `lagrer historiske identer når personen opprettes`() {
        val historiskIdent = "12345"
        val aktivIdent = "67890"
        val personRepository = FakePersonRepository()
        val pdlGateway = FakePdlGateway(
            identerForPerson = mapOf(
                aktivIdent to listOf(
                    PdlIdent(historiskIdent, historisk = true),
                    PdlIdent(aktivIdent, historisk = false),
                )
            )
        )

        val person = PersonService(personRepository, pdlGateway).hentEllerLagrePerson(aktivIdent)

        assertThat(personRepository.hentPerson(historiskIdent)?.id()).isEqualTo(person.id())
        assertThat(personRepository.hentPerson(historiskIdent)?.ident).isEqualTo(aktivIdent)
    }
}

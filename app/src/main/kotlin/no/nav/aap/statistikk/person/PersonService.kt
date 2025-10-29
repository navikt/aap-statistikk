package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.repository.RepositoryProvider

class PersonService(private val personRepository: IPersonRepository) {
    constructor(repositoryProvider: RepositoryProvider) : this(repositoryProvider.provide())

    fun hentEllerLagrePerson(ident: String): Person {
        val person = Person(ident)

        val uthentet = personRepository.hentPerson(ident)

        if (uthentet == null) {
            val id = personRepository.lagrePerson(person)
            return person.medId(id = id)
        }

        return uthentet
    }
}
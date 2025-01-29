package no.nav.aap.statistikk.person

class PersonService(private val personRepository: IPersonRepository) {
    fun hentEllerLagrePerson(ident: String): Person {
        val person = Person(ident)

        val uthentet = personRepository.hentPerson(ident) // // ?: it.copy(id = personRepository.lagrePerson(it))

        if (uthentet == null) {
            val id = personRepository.lagrePerson(person)
            return person.copy(id = id)
        }

        return uthentet
    }
}
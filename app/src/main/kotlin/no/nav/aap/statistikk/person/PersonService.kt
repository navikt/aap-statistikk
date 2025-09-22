package no.nav.aap.statistikk.person

class PersonService(private val personRepository: IPersonRepository) {
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
package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.repository.Repository

interface IPersonRepository : Repository {
    fun lagrePerson(person: Person, identer: Set<String>): Long
    fun slåSammenPersoner(beholdPersonId: Long, fjernPersonIder: Set<Long>, identer: Set<String>)
    fun hentPerson(ident: String): Person?
}

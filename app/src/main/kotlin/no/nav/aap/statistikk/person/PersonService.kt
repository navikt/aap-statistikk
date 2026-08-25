package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.integrasjoner.pdl.PdlGateway
import no.nav.aap.statistikk.skjerming.SkjermingService
import org.slf4j.LoggerFactory

class PersonService(private val personRepository: IPersonRepository, private val pdlGateway: PdlGateway) {
    private val skjermingService = SkjermingService(pdlGateway)
    private val logger = LoggerFactory.getLogger(javaClass)

    constructor(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ) : this(repositoryProvider.provide(), gatewayProvider.provide())

    fun hentEllerLagrePerson(ident: Ident): Person {
        val identer = pdlGateway.hentIdenter(ident)
        val aktiveIdenter = identer.filterNot { it.historisk }
        require(aktiveIdenter.size == 1) {
            "Forventet nøyaktig én aktiv folkeregisterident fra PDL, fant ${aktiveIdenter.size}."
        }
        val aktivIdent = Ident(aktiveIdenter.single().ident)
        val identverdier = identer.map { Ident(it.ident) }
        val eksisterendePersoner = finnEksisterendePersoner(identverdier)
        val erSkjermet = skjermingService.erSkjermet(identverdier)

        return when {
            eksisterendePersoner.isEmpty() -> {
                val person = Person(aktivIdent, skjermet = erSkjermet)
                person.medId(id = personRepository.lagrePerson(person, identverdier.toSet()))
            }

            else -> slåSammenOgOppdaterPerson(
                eksisterendePersoner,
                aktivIdent,
                identverdier.toSet(),
                erSkjermet,
            )
        }
    }

    private fun finnEksisterendePersoner(identer: List<Ident>): List<Person> {
        return identer
            .mapNotNull(personRepository::hentPerson)
            .distinctBy { it.id() }
    }

    private fun slåSammenOgOppdaterPerson(
        personer: List<Person>,
        aktivIdent: Ident,
        identer: Set<Ident>,
        erSkjermet: Boolean,
    ): Person {
        val person = personer.firstOrNull { it.ident == aktivIdent }
            ?: personer.minBy { requireNotNull(it.id()) }
        val id = requireNotNull(person.id()) { "Person må ha ID for å kunne oppdateres." }
        val personIderSomSkalFjernes = personer.mapNotNull(Person::id).toSet() - id
        if (personIderSomSkalFjernes.isNotEmpty()) {
            logger.warn("Slår sammen ${personer.size} personrader fordi PDL knytter identene til samme person.")
        }
        personRepository.slåSammenPersoner(id, personIderSomSkalFjernes, identer)

        val oppdatertPerson = Person(ident = aktivIdent, skjermet = erSkjermet, id = id)

        personRepository.lagrePerson(oppdatertPerson, identer)
        return oppdatertPerson
    }
}
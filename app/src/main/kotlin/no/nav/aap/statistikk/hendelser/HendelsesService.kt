package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.BQSak
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepository


class HendelsesService(
    private val hendelsesRepository: IHendelsesRepository,
    private val sakRepository: SakRepository,
    private val personRepository: PersonRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val bigQueryRepository: IBQRepository
) {
    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = hentEllerSettInnPerson(hendelse)
        val sak = hentEllerSettInnSak(hendelse, person)

        val behandlingId = hentEllerLagreBehandlingId(hendelse, sak)

        hendelsesRepository.lagreHendelse(hendelse, sak.id!!, behandlingId)

        val bqSak = BQSak(
            saksnummer = sak.saksnummer,
            behandlinger = listOf(
                BQBehandling(
                    behandlingUUID = behandlingRepository.hent(behandlingId).referanse.toString()
                )
            )
        )
        bigQueryRepository.lagre(bqSak)
    }

    private fun hentEllerLagreBehandlingId(
        dto: StoppetBehandling,
        sak: Sak?
    ): Long {
        val behandling = behandlingRepository.hent(dto.behandlingReferanse)
        val behandlingId = if (behandling != null) {
            behandling.id!!
        } else {
            behandlingRepository.lagre(
                Behandling(
                    referanse = dto.behandlingReferanse,
                    sak = sak!!,
                    typeBehandling = dto.behandlingType,
                    opprettetTid = dto.behandlingOpprettetTidspunkt
                )
            )
        }
        return behandlingId
    }

    private fun hentEllerSettInnSak(
        dto: StoppetBehandling,
        person: Person
    ): Sak {
        var sak = sakRepository.hentSakEllernull(dto.saksnummer)
        if (sak == null) {
            val sakId = sakRepository.settInnSak(
                Sak(
                    id = null,
                    saksnummer = dto.saksnummer,
                    person = person
                )
            )
            sak = sakRepository.hentSak(sakId)
        }
        return sak
    }

    private fun hentEllerSettInnPerson(dto: StoppetBehandling): Person {
        var person = personRepository.hentPerson(dto.ident)
        if (person == null) {
            personRepository.lagrePerson(Person(dto.ident))
        }
        person = personRepository.hentPerson(dto.ident)!!
        return person
    }
}
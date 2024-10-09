package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.person.IPersonRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.IBigQueryKvitteringRepository
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


class HendelsesService(
    private val sakRepository: SakRepository,
    private val bigQueryKvitteringRepository: IBigQueryKvitteringRepository,
    private val personRepository: IPersonRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val bigQueryRepository: IBQRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = hentEllerSettInnPerson(hendelse)
        val sak = hentEllerSettInnSak(hendelse, person)

        val behandlingId = hentEllerLagreBehandlingId(hendelse, sak)

        lagreSakInfoTilBigquery(sak, behandlingId, hendelse.versjon)
    }

    private fun lagreSakInfoTilBigquery(sak: Sak, behandlingId: Long, versjon: String) {
        val behandling = behandlingRepository.hent(behandlingId)
        val sekvensNummer = bigQueryKvitteringRepository.lagreKvitteringForSak(sak, behandling)

        // TODO - kun om endring siden sist. somehow!?
        val bqSak = BQBehandling(
            sekvensNummer = sekvensNummer,
            saksnummer = sak.saksnummer,
            behandlingUUID = behandling.referanse.toString(),
            behandlingType = behandling.typeBehandling.toString().uppercase(),
            tekniskTid = LocalDateTime.now(clock),
            avsender = KELVIN,
            verson = versjon,
            aktorId = sak.person.ident,
            mottattTid = behandling.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            registrertTid = behandling.opprettetTid.truncatedTo(ChronoUnit.SECONDS),
        )
        bigQueryRepository.lagre(bqSak)
    }

    private fun hentEllerLagreBehandlingId(
        dto: StoppetBehandling,
        sak: Sak?
    ): Long {
        val behandling = Behandling(
            referanse = dto.behandlingReferanse,
            sak = sak!!,
            typeBehandling = dto.behandlingType,
            opprettetTid = dto.behandlingOpprettetTidspunkt,
            mottattTid = dto.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            status = dto.status,
            versjon = Versjon(verdi = dto.versjon)
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id
        val behandlingId =
            eksisterendeBehandlingId
                ?.also { behandlingRepository.oppdaterBehandling(behandling.copy(id = eksisterendeBehandlingId)) }
                ?: behandlingRepository.opprettBehandling(behandling)
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
                    person = person,
                    sistOppdatert = LocalDateTime.now(clock),
                    sakStatus = dto.sakStatus
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
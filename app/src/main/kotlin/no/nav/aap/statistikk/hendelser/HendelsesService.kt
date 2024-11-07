package no.nav.aap.statistikk.hendelser

import io.micrometer.core.instrument.Counter
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.BehandlingStatus
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.api.tilDomene
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.tilDomene
import no.nav.aap.statistikk.person.IPersonRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


class HendelsesService(
    private val sakRepository: SakRepository,
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val personRepository: IPersonRepository,
    private val behandlingRepository: IBehandlingRepository,
    private val hendelseLagretCounter: Counter,
    private val sakStatistikkService: SaksStatistikkService,
    private val clock: Clock = Clock.systemUTC()
) {
    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = hentEllerSettInnPerson(hendelse)
        val sak = hentEllerSettInnSak(hendelse, person)

        val behandlingId = hentEllerLagreBehandlingId(hendelse, sak)

        if (hendelse.status == BehandlingStatus.AVSLUTTET) {
            avsluttetBehandlingService.lagre(hendelse.avsluttetBehandling!!.tilDomene())
        }

        val vedtakTid = hendelse.avklaringsbehov.utledVedtakTid()

        sakStatistikkService.lagreSakInfoTilBigquery(
            sak,
            behandlingId,
            hendelse.versjon,
            hendelse.hendelsesTidspunkt,
            vedtakTidspunkt = vedtakTid
        )
        hendelseLagretCounter.increment()
    }

    private fun hentEllerLagreBehandlingId(
        dto: StoppetBehandling,
        sak: Sak
    ): Long {
        val behandling = Behandling(
            referanse = dto.behandlingReferanse,
            sak = sak,
            typeBehandling = dto.behandlingType.tilDomene(),
            opprettetTid = dto.behandlingOpprettetTidspunkt,
            mottattTid = dto.mottattTid.truncatedTo(ChronoUnit.SECONDS),
            status = dto.status.tilDomene(),
            versjon = Versjon(verdi = dto.versjon),
            relaterteIdenter = dto.identerForSak,
            sisteSaksbehandler = dto.avklaringsbehov.sistePersonPåBehandling(),
            gjeldendeAvklaringsBehov = dto.avklaringsbehov.utledGjeldendeAvklaringsBehov()
        )
        val eksisterendeBehandlingId = behandlingRepository.hent(dto.behandlingReferanse)?.id

        val relatertBehandlingUUID = dto.relatertBehandling
        val relatertBehadling =
            relatertBehandlingUUID?.let { behandlingRepository.hent(relatertBehandlingUUID) }

        val behandlingId =
            eksisterendeBehandlingId
                ?.also {
                    behandlingRepository.oppdaterBehandling(
                        behandling.copy(
                            id = eksisterendeBehandlingId,
                            relatertBehandlingId = relatertBehadling?.id
                        )
                    )
                }
                ?: behandlingRepository.opprettBehandling(behandling.copy(relatertBehandlingId = relatertBehadling?.id))
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


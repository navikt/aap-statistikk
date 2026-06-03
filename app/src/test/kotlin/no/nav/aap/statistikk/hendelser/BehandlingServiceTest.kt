package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.ÅrsakTilOpprettelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling as DomeneTypeBehandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.UUID

class BehandlingServiceTest {
    @Test
    fun `hentEllerLagreBehandling bruker lock-varianten ved oppdatering av eksisterende behandling`() {
        val referanse = UUID.randomUUID()
        val sak = testSak()
        val eksisterende = eksisterendeBehandling(sak, referanse)

        val repository = object : IBehandlingRepository {
            var oppdateringKalt = false
            var lockHentKalt = false

            override fun opprettBehandling(behandling: Behandling): BehandlingId {
                error("Skal ikke opprette ny behandling")
            }

            override fun oppdaterBehandling(behandling: Behandling) {
                oppdateringKalt = true
            }

            override fun invaliderOgLagreNyHistorikk(behandling: Behandling) = Unit

            override fun hent(referanse: UUID): Behandling? {
                error("Skal ikke bruke ulåst hent(referanse)")
            }

            override fun hentEllerNull(id: BehandlingId): Behandling? = null

            override fun hent(id: BehandlingId): Behandling = eksisterende

            override fun hentBehandlingForUpdate(id: BehandlingId): Behandling = eksisterende

            override fun hentBehandlingForUpdate(referanse: UUID): Behandling? {
                lockHentKalt = true
                return eksisterende
            }
        }

        val behandlingService = BehandlingService(repository, SkjermingService(FakePdlGateway(emptyMap())))
        val resultat = behandlingService.hentEllerLagreBehandling(testHendelse(referanse), sak)

        assertThat(repository.lockHentKalt).isTrue()
        assertThat(repository.oppdateringKalt).isTrue()
        assertThat(resultat.id).isEqualTo(eksisterende.id)
    }

    @Test
    fun `hentEllerLagreBehandling håndterer duplicate key ved oppretting og låser eksisterende rad`() {
        val referanse = UUID.randomUUID()
        val sak = testSak()
        val eksisterende = eksisterendeBehandling(sak, referanse)

        val repository = object : IBehandlingRepository {
            var lockHentAntall = 0
            var opprettKalt = false
            var oppdateringKalt = false

            override fun opprettBehandling(behandling: Behandling): BehandlingId {
                opprettKalt = true
                throw SQLException("duplicate key", "23505")
            }

            override fun oppdaterBehandling(behandling: Behandling) {
                oppdateringKalt = true
            }

            override fun invaliderOgLagreNyHistorikk(behandling: Behandling) = Unit

            override fun hent(referanse: UUID): Behandling? = null

            override fun hentEllerNull(id: BehandlingId): Behandling? = null

            override fun hent(id: BehandlingId): Behandling = eksisterende

            override fun hentBehandlingForUpdate(id: BehandlingId): Behandling = eksisterende

            override fun hentBehandlingForUpdate(referanse: UUID): Behandling? {
                lockHentAntall++
                return if (lockHentAntall == 1) null else eksisterende
            }
        }

        val behandlingService = BehandlingService(repository, SkjermingService(FakePdlGateway(emptyMap())))
        val resultat = behandlingService.hentEllerLagreBehandling(testHendelse(referanse), sak)

        assertThat(repository.opprettKalt).isTrue()
        assertThat(repository.lockHentAntall).isEqualTo(2)
        assertThat(repository.oppdateringKalt).isTrue()
        assertThat(resultat.id).isEqualTo(eksisterende.id)
    }
}

private fun testHendelse(referanse: UUID): StoppetBehandling {
    return StoppetBehandling(
        saksnummer = "1234",
        behandlingReferanse = referanse,
        behandlingOpprettetTidspunkt = LocalDateTime.now(),
        behandlingStatus = Status.OPPRETTET,
        behandlingType = TypeBehandling.Førstegangsbehandling,
        ident = "12345678901",
        versjon = "versjon",
        avklaringsbehov = listOf(
            AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        tidsstempel = LocalDateTime.now(),
                        endretAv = "Z999999"
                    )
                )
            )
        ),
        mottattTid = LocalDateTime.now().minusDays(1),
        sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.OPPRETTET,
        hendelsesTidspunkt = LocalDateTime.now(),
        vurderingsbehov = listOf(Vurderingsbehov.SØKNAD),
        årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD
    )
}

private fun testSak(): Sak {
    return Sak(
        id = 1L,
        saksnummer = Saksnummer("1234"),
        person = Person(ident = "12345678901", id = 1L),
        sakStatus = SakStatus.OPPRETTET,
        sistOppdatert = LocalDateTime.now()
    )
}

private fun eksisterendeBehandling(sak: Sak, referanse: UUID): Behandling {
    return Behandling(
        id = BehandlingId(123L),
        referanse = referanse,
        sak = sak,
        typeBehandling = DomeneTypeBehandling.Førstegangsbehandling,
        status = BehandlingStatus.OPPRETTET,
        opprettetTid = LocalDateTime.now(),
        mottattTid = LocalDateTime.now().minusDays(1),
        versjon = Versjon("versjon"),
        søknadsformat = SøknadsFormat.PAPIR,
        oppdatertTidspunkt = LocalDateTime.now(),
        relaterteIdenter = emptyList()
    )
}

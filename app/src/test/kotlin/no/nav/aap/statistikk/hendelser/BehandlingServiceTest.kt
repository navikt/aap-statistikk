package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.ÅrsakTilOpprettelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.ReturÅrsakkobling
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.Vurderingsbehov
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.avsluttetBehandlingDTO
import no.nav.aap.statistikk.testutils.fakes.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.UUID
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as BehovStatus
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.ResultatKode as KontraktResultatKode
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov as KontraktVurderingsbehov
import no.nav.aap.statistikk.behandling.TypeBehandling as DomeneTypeBehandling

class BehandlingServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `ny og eksisterende behandling får samme vedtaksdata men beholder egne historikktidspunkter`(
        finnesFraFør: Boolean,
    ) {
        val tidspunkt = LocalDateTime.of(2026, 11, 18, 12, 0)
        val referanse = UUID.randomUUID()
        val sak = testSak()
        val behovTid = tidspunkt.minusMinutes(2)
        val vedtakTid = tidspunkt.minusMinutes(3)
        val dto = testHendelse(referanse).copy(
            behandlingStatus = Status.AVSLUTTET,
            hendelsesTidspunkt = tidspunkt,
            tidspunktSisteEndring = tidspunkt.minusMinutes(1),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FATTE_VEDTAK,
                    status = BehovStatus.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = BehovStatus.AVSLUTTET,
                            tidsstempel = behovTid,
                            endretAv = "Z111111",
                        ),
                    ),
                ),
            ),
            avsluttetBehandling = avsluttetBehandlingDTO().copy(
                resultat = KontraktResultatKode.INNVILGET,
                vedtakstidspunkt = vedtakTid,
            ),
        )
        val eksisterende = if (finnesFraFør) eksisterendeBehandling(sak, referanse) else null
        val service = BehandlingService(FakeBehandlingRepository(), SkjermingService(FakePdlGateway(emptyMap())))

        val resultat = service.konstruerBehandling(dto, sak, eksisterende)

        assertThat(resultat.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(resultat.resultat).isEqualTo(ResultatKode.INNVILGET)
        assertThat(resultat.sisteSaksbehandler).isEqualTo("Z111111")
        assertThat(resultat.sisteLøsteAvklaringsbehov).isEqualTo(Definisjon.FATTE_VEDTAK)
        assertThat(resultat.sisteSaksbehandlerSomLøstebehov).isEqualTo("Z111111")
        assertThat(resultat.sistLøsteAvklaringsbehovTidspunkt).isEqualTo(if (finnesFraFør) behovTid else null)
        assertThat(resultat.ansvarligBeslutter).isEqualTo("Z111111")
        assertThat(resultat.vedtakstidspunkt).isEqualTo(vedtakTid)
        assertThat(resultat.gjeldendeAvklaringsBehov).isNull()
        assertThat(resultat.gjeldendeAvklaringsbehovStatus).isNull()
        assertThat(resultat.venteÅrsak).isNull()
        assertThat(resultat.returÅrsak).isNull()
        assertThat(resultat.oppdatertTidspunkt()).isEqualTo(if (finnesFraFør) tidspunkt else behovTid)
        assertThat(resultat.hendelser).hasSize(if (finnesFraFør) 1 else 0)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `ny og eksisterende behandling får samme returdata og venteårsak`(finnesFraFør: Boolean) {
        val tidspunkt = LocalDateTime.of(2026, 11, 18, 12, 0)
        val referanse = UUID.randomUUID()
        val sak = testSak()
        val dto = testHendelse(referanse).copy(
            behandlingStatus = Status.UTREDES,
            hendelsesTidspunkt = tidspunkt,
            avklaringsbehov = returHendelser + AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = Definisjon.MANUELT_SATT_PÅ_VENT,
                status = BehovStatus.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = BehovStatus.OPPRETTET,
                        tidsstempel = tidspunkt.minusMinutes(1),
                        endretAv = "Z222222",
                        årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER,
                    ),
                ),
            ),
        )
        val eksisterende = if (finnesFraFør) eksisterendeBehandling(sak, referanse) else null
        val service = BehandlingService(FakeBehandlingRepository(), SkjermingService(FakePdlGateway(emptyMap())))

        val resultat = service.konstruerBehandling(dto, sak, eksisterende)

        assertThat(resultat.behandlingStatus()).isEqualTo(BehandlingStatus.UTREDES)
        assertThat(resultat.gjeldendeAvklaringsBehov).isEqualTo(Definisjon.AVKLAR_SYKDOM)
        assertThat(resultat.gjeldendeAvklaringsbehovStatus).isEqualTo(BehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER)
        assertThat(resultat.gjeldendeStegGruppe).isEqualTo(Definisjon.AVKLAR_SYKDOM.løsesISteg.gruppe)
        assertThat(resultat.sisteSaksbehandler).isEqualTo("Z222222")
        assertThat(resultat.venteÅrsak).isEqualTo("VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER")
        assertThat(resultat.returÅrsak).isEqualTo("MANGLENDE_UTREDNING")
        assertThat(resultat.returÅrsakkoblinger)
            .containsExactly(ReturÅrsakkobling(Definisjon.AVKLAR_SYKDOM, listOf("MANGLENDE_UTREDNING")))
        assertThat(resultat.ansvarligBeslutter).isNull()
        assertThat(resultat.resultat).isNull()
        assertThat(resultat.vedtakstidspunkt).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `tomme behov gir ingen behovsdata og fjerner tidligere verdier`(finnesFraFør: Boolean) {
        val referanse = UUID.randomUUID()
        val sak = testSak()
        val dto = testHendelse(referanse).copy(avklaringsbehov = emptyList())
        val eksisterende = if (finnesFraFør) {
            eksisterendeBehandling(sak, referanse).copy(
                gjeldendeAvklaringsBehov = Definisjon.AVKLAR_SYKDOM,
                gjeldendeAvklaringsbehovStatus = BehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                gjeldendeStegGruppe = Definisjon.AVKLAR_SYKDOM.løsesISteg.gruppe,
                sisteLøsteAvklaringsbehov = Definisjon.FATTE_VEDTAK,
                sisteSaksbehandlerSomLøstebehov = "Beslutter",
                sistLøsteAvklaringsbehovTidspunkt = dto.hendelsesTidspunkt.minusMinutes(1),
                sisteSaksbehandler = "Saksbehandler",
                ansvarligBeslutter = "Beslutter",
                venteÅrsak = "VENTER_PÅ_MASKINELL_AVKLARING",
                returÅrsak = "MANGLENDE_UTREDNING",
                returÅrsakkoblinger = listOf(
                    ReturÅrsakkobling(Definisjon.AVKLAR_SYKDOM, listOf("MANGLENDE_UTREDNING")),
                ),
            )
        } else {
            null
        }
        val service = BehandlingService(FakeBehandlingRepository(), SkjermingService(FakePdlGateway(emptyMap())))

        val resultat = service.konstruerBehandling(dto, sak, eksisterende)

        assertThat(resultat.gjeldendeAvklaringsBehov).isNull()
        assertThat(resultat.gjeldendeAvklaringsbehovStatus).isNull()
        assertThat(resultat.gjeldendeStegGruppe).isNull()
        assertThat(resultat.sisteLøsteAvklaringsbehov).isNull()
        assertThat(resultat.sisteSaksbehandlerSomLøstebehov).isNull()
        assertThat(resultat.sistLøsteAvklaringsbehovTidspunkt).isNull()
        assertThat(resultat.sisteSaksbehandler).isNull()
        assertThat(resultat.ansvarligBeslutter).isNull()
        assertThat(resultat.venteÅrsak).isNull()
        assertThat(resultat.returÅrsak).isNull()
        assertThat(resultat.returÅrsakkoblinger).isEmpty()
    }

    @Test
    fun `hentEllerLagreBehandling bruker lock-varianten ved oppdatering av eksisterende behandling`() {
        val referanse = UUID.randomUUID()
        val sak = testSak()
        val eksisterende = eksisterendeBehandling(sak, referanse)
        var lagretBehandling: Behandling? = null

        val repository = object : IBehandlingRepository {
            var oppdateringKalt = false
            var lockHentKalt = false

            override fun opprettBehandling(behandling: Behandling): BehandlingId {
                error("Skal ikke opprette ny behandling")
            }

            override fun oppdaterBehandling(behandling: Behandling) {
                oppdateringKalt = true
                lagretBehandling = behandling
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
        assertThat(lagretBehandling?.årsaker).containsExactly(Vurderingsbehov.SØKNAD)
    }

    @Test
    fun `hentEllerLagreBehandling oppdaterer årsaker på eksisterende behandling`() {
        val referanse = UUID.randomUUID()
        val sak = testSak()
        val eksisterende = eksisterendeBehandling(sak, referanse)
        val repository = FakeBehandlingRepository()
        repository.opprettBehandling(eksisterende)

        val behandlingService = BehandlingService(repository, SkjermingService(FakePdlGateway(emptyMap())))
        behandlingService.hentEllerLagreBehandling(
            testHendelse(
                referanse,
                listOf(KontraktVurderingsbehov.SØKNAD, KontraktVurderingsbehov.MELDEKORT),
            ),
            sak,
        )

        assertThat(repository.hent(referanse)?.årsaker)
            .containsExactly(Vurderingsbehov.SØKNAD, Vurderingsbehov.MELDEKORT)
    }

    @Test
    fun `hentEllerLagreBehandling kaster exception ved duplicate key ved oppretting`() {
        val referanse = UUID.randomUUID()
        val sak = testSak()

        val repository = object : IBehandlingRepository {
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

            override fun hent(id: BehandlingId): Behandling = error("Skal ikke hente behandling")

            override fun hentBehandlingForUpdate(id: BehandlingId): Behandling = error(
                "Skal ikke hente behandling med id-lock",
            )

            override fun hentBehandlingForUpdate(referanse: UUID): Behandling? = null
        }

        val behandlingService = BehandlingService(repository, SkjermingService(FakePdlGateway(emptyMap())))
        val exception = assertThrows<SQLException> {
            behandlingService.hentEllerLagreBehandling(testHendelse(referanse), sak)
        }

        assertThat(repository.opprettKalt).isTrue()
        assertThat(repository.oppdateringKalt).isFalse()
        assertThat(exception.sqlState).isEqualTo("23505")
    }
}

private fun testHendelse(
    referanse: UUID,
    vurderingsbehov: List<KontraktVurderingsbehov> = listOf(KontraktVurderingsbehov.SØKNAD),
): StoppetBehandling = StoppetBehandling(
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
                    endretAv = "Z999999",
                ),
            ),
        ),
    ),
    mottattTid = LocalDateTime.now().minusDays(1),
    sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.OPPRETTET,
    hendelsesTidspunkt = LocalDateTime.now(),
    vurderingsbehov = vurderingsbehov,
    årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD,
)

private fun testSak(): Sak = Sak(
    id = 1L,
    saksnummer = Saksnummer("1234"),
    person = Person(ident = no.nav.aap.statistikk.person.Ident("12345678901"), id = 1L),
    sakStatus = SakStatus.OPPRETTET,
    sistOppdatert = LocalDateTime.now(),
)

private fun eksisterendeBehandling(sak: Sak, referanse: UUID): Behandling = Behandling(
    id = BehandlingId(123L),
    referanse = referanse,
    sak = sak,
    typeBehandling = DomeneTypeBehandling.Førstegangsbehandling,
    status = BehandlingStatus.OPPRETTET,
    opprettetTid = LocalDateTime.now(),
    mottattTid = LocalDateTime.now().minusDays(1),
    versjon = Versjon("versjon"),
    søknadsformat = SøknadsFormat.PAPIR,
    årsaker = listOf(Vurderingsbehov.SØKNAD),
    oppdatertTidspunkt = LocalDateTime.now(),
    relaterteIdenter = emptyList(),
)

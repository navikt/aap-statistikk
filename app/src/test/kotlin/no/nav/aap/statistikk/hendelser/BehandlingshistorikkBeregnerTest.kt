package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilRetur
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.behandling.AvklaringsbehovStatus
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.BehandlingTilstand
import no.nav.aap.statistikk.behandling.Behandlingssteggruppe
import no.nav.aap.statistikk.behandling.ReturÅrsakTilstand
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.tilAvklaringsbehovKode
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.avsluttetBehandlingDTO
import no.nav.aap.statistikk.testutils.builders.opprettTestStoppetBehandling
import no.nav.aap.statistikk.testutils.fakes.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime
import java.util.*

class BehandlingshistorikkBeregnerTest {
    private val start = LocalDateTime.of(2026, 11, 19, 10, 0)

    @ParameterizedTest
    @CsvSource("8, 9, 8, 8", ", 9, 9, 9", ", , 0, ")
    fun `avslutning uten behov beholder egne tidsfallbacks`(
        vedtakMinutter: Long?,
        endringMinutter: Long?,
        forventetHendelseMinutter: Long,
        forventetVedtakMinutter: Long?,
    ) {
        val dto = hendelseMedReturOgVent().copy(
            avklaringsbehov = emptyList(),
            behandlingStatus = no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.AVSLUTTET,
            avsluttetBehandling = avsluttetBehandlingDTO().copy(
                vedtakstidspunkt = vedtakMinutter?.let(start::plusMinutes),
            ),
            tidspunktSisteEndring = endringMinutter?.let(start::plusMinutes),
        )
        val grunnlagBehandling = utgangspunkt(dto)
        val behandling = grunnlagBehandling.copy(
            tilstand = grunnlagBehandling.nåværendeTilstand().copy(vedtakstidspunkt = start.minusDays(1)),
        )

        val historikk = BehandlingshistorikkBeregner().beregn(dto, behandling).hendelser

        assertThat(historikk).hasSize(2)
        assertThat(historikk.first().hendelsesTidspunkt).isEqualTo(start)
        assertThat(historikk.first().tilstand.vedtakstidspunkt).isNull()
        val avslutning = historikk.last()
        assertThat(avslutning.tilstand.status).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(avslutning.tidspunkt).isEqualTo(endringMinutter?.let(start::plusMinutes))
        assertThat(avslutning.hendelsesTidspunkt).isEqualTo(start.plusMinutes(forventetHendelseMinutter))
        assertThat(avslutning.tilstand.vedtakstidspunkt).isEqualTo(forventetVedtakMinutter?.let(start::plusMinutes))
        assertThat(avslutning.tilstand.ansvarligBeslutter).isNull()
    }

    @Test
    fun `avslutning etter behovshistorikk bruker vedtak og beslutter fra utgangspunktet`() {
        val dto = hendelseMedReturOgVent().copy(
            behandlingStatus = no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.AVSLUTTET,
            avsluttetBehandling = avsluttetBehandlingDTO().copy(vedtakstidspunkt = start.plusMinutes(8)),
            tidspunktSisteEndring = start.plusMinutes(9),
        )
        val inngang = BehandlingHendelse(
            tidspunkt = start.minusMinutes(2),
            hendelsesTidspunkt = start.minusMinutes(2),
            tilstand = BehandlingTilstand(
                gjeldendeAvklaringsbehovStatus = null,
                versjon = Versjon(dto.versjon),
                status = BehandlingStatus.OPPRETTET,
                mottattTid = start,
                søknadsformat = SøknadsFormat.DIGITAL,
                relatertBehandlingReferanse = null,
            ),
        )
        val behandling = utgangspunkt(dto)
            .leggTilHendelse(inngang)
            .leggTilHendelse(
                inngang.copy(
                    hendelsesTidspunkt = start.minusMinutes(1),
                    tilstand = inngang.tilstand.copy(
                        status = BehandlingStatus.AVSLUTTET,
                        vedtakstidspunkt = start.plusMinutes(7),
                        ansvarligBeslutter = "Lagret beslutter",
                    ),
                ),
            )

        val historikk = BehandlingshistorikkBeregner().beregn(dto, behandling).hendelser

        assertThat(historikk).hasSize(8)
        assertThat(historikk.take(2)).isEqualTo(behandling.hendelser)
        val vedtak = historikk.single { it.hendelsesTidspunkt == start.plusMinutes(3) }
        assertThat(vedtak.tilstand.vedtakstidspunkt).isEqualTo(start.plusMinutes(7))
        assertThat(vedtak.tilstand.ansvarligBeslutter).isEqualTo("Beslutter")
        val avslutning = historikk.last()
        assertThat(avslutning.tilstand.status).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(avslutning.tidspunkt).isNull()
        assertThat(avslutning.hendelsesTidspunkt).isEqualTo(start.plusMinutes(8))
        assertThat(avslutning.tilstand.vedtakstidspunkt).isEqualTo(start.plusMinutes(7))
        assertThat(avslutning.tilstand.ansvarligBeslutter).isEqualTo("Lagret beslutter")
        assertThat(
            avslutning.tilstand.sisteLøsteAvklaringsbehov,
        ).isEqualTo(Definisjon.FATTE_VEDTAK.tilAvklaringsbehovKode())
        assertThat(avslutning.tilstand.sisteSaksbehandlerSomLøstebehov).isEqualTo("Beslutter")
        assertThat(avslutning.tilstand.sistLøsteAvklaringsbehovTidspunkt).isNull()
    }

    private fun utgangspunkt(dto: StoppetBehandling) = Behandling(
        referanse = dto.behandlingReferanse,
        sak = Sak(
            saksnummer = Saksnummer(dto.saksnummer),
            person = Person(ident = no.nav.aap.statistikk.person.Ident(dto.ident)),
            sistOppdatert = start,
        ),
        typeBehandling = dto.behandlingType.tilDomene(),
        opprettetTid = dto.behandlingOpprettetTidspunkt,
        tilstand = BehandlingTilstand(
            status = dto.behandlingStatus.tilDomene(),
            mottattTid = dto.mottattTid,
            versjon = Versjon(dto.versjon),
            søknadsformat = SøknadsFormat.DIGITAL,
        ),
    )

    @Test
    fun `historikken bruker behovstilstanden på hvert tidspunkt`() {
        val dto = hendelseMedReturOgVent()
        val sak = Sak(
            saksnummer = Saksnummer(dto.saksnummer),
            person = Person(ident = no.nav.aap.statistikk.person.Ident(dto.ident)),
            sistOppdatert = start,
        )
        val behandling = BehandlingService(FakeBehandlingRepository(), SkjermingService(FakePdlGateway()))
            .konstruerBehandling(dto, sak, eksisterendeBehandlingKanskje = null)

        val historikk = BehandlingshistorikkBeregner().beregn(dto, behandling).hendelser

        assertThat(historikk.map { it.hendelsesTidspunkt })
            .containsExactlyElementsOf(listOf(0L, 1L, 3L, 4L, 5L, 6L).map { start.plusMinutes(it) })
        assertThat(historikk.map { it.tilstand.gjeldendeAvklaringsBehov }).containsExactly(
            null,
            Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
            null,
            Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
            Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
            Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
        )
        assertThat(historikk.map { it.tilstand.sisteSaksbehandler })
            .containsExactly(null, null, "Beslutter", "Kvalitetssikrer", "Ventebehandler", "Ventebehandler")
        assertThat(historikk.map { it.tilstand.ansvarligBeslutter })
            .containsExactly(null, null, "Beslutter", null, null, null)
        assertThat(historikk.map { it.tilstand.returÅrsak }).containsExactly(
            null,
            null,
            null,
            "MANGELFULL_BEGRUNNELSE",
            "MANGELFULL_BEGRUNNELSE",
            "MANGELFULL_BEGRUNNELSE",
        )
        assertThat(historikk.map { it.tilstand.vedtakstidspunkt }).containsOnlyNulls()

        val påVent = historikk.single { it.hendelsesTidspunkt == start.plusMinutes(5) }
        assertThat(
            påVent.tilstand.gjeldendeAvklaringsbehovStatus,
        ).isEqualTo(AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER)
        assertThat(påVent.tilstand.gjeldendeStegGruppe).isEqualTo(Behandlingssteggruppe.SYKDOM)
        assertThat(
            påVent.tilstand.sisteLøsteAvklaringsbehov,
        ).isEqualTo(Definisjon.FATTE_VEDTAK.tilAvklaringsbehovKode())
        assertThat(påVent.tilstand.sisteSaksbehandlerSomLøstebehov).isEqualTo("Beslutter")
        assertThat(påVent.tilstand.sistLøsteAvklaringsbehovTidspunkt).isEqualTo(start.plusMinutes(3))
        assertThat(påVent.tilstand.venteÅrsak).isEqualTo("VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER")
        assertThat(påVent.tilstand.returÅrsakkoblinger).containsExactly(
            ReturÅrsakTilstand(
                Definisjon.AVKLAR_SYKDOM.tilAvklaringsbehovKode(),
                listOf("MANGLENDE_UTREDNING", "MANGELFULL_BEGRUNNELSE"),
            ),
        )
        assertThat(historikk.last().tilstand.venteÅrsak).isNull()
        assertThat(historikk.first().tilstand.status).isEqualTo(BehandlingStatus.OPPRETTET)
        assertThat(historikk.last().tilstand.status).isEqualTo(BehandlingStatus.UTREDES)
    }

    private fun hendelseMedReturOgVent() = opprettTestStoppetBehandling(
        behandlingReferanse = UUID.randomUUID(),
        behandlingOpprettetTidspunkt = start,
        hendelsesTidspunkt = start.plusMinutes(6),
        mottattTid = start,
        behandlingStatus = no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.UTREDES,
        avklaringsbehov = listOf(
            behov(
                Definisjon.AVKLAR_SYKDOM,
                EndringDTO(Status.OPPRETTET, start.plusMinutes(1), endretAv = "Kelvin"),
                EndringDTO(Status.AVSLUTTET, start.plusMinutes(2), endretAv = "Saksbehandler"),
                EndringDTO(
                    Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    start.plusMinutes(4),
                    endretAv = "Kvalitetssikrer",
                    årsakTilRetur = listOf(
                        ÅrsakTilRetur(ÅrsakTilReturKode.MANGLENDE_UTREDNING),
                        ÅrsakTilRetur(ÅrsakTilReturKode.MANGELFULL_BEGRUNNELSE),
                    ),
                ),
            ),
            behov(
                Definisjon.FATTE_VEDTAK,
                EndringDTO(Status.AVSLUTTET, start.plusMinutes(3), endretAv = "Beslutter"),
            ),
            behov(
                Definisjon.MANUELT_SATT_PÅ_VENT,
                EndringDTO(
                    Status.OPPRETTET,
                    start.plusMinutes(5),
                    endretAv = "Ventebehandler",
                    årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER,
                ),
                EndringDTO(Status.AVSLUTTET, start.plusMinutes(6), endretAv = "Kelvin"),
            ),
        ),
    )

    private fun behov(definisjon: Definisjon, vararg endringer: EndringDTO) = AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = definisjon,
        status = endringer.last().status,
        endringer = endringer.toList(),
    )

    @Test
    fun `beregner historikk for fullført førstegangsbehandling`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")

        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            årsaker = listOf(),
            oppdatertTidspunkt = null,
            hendelser = listOf(),
            tilstand = BehandlingTilstand(
                status = hendelse.behandlingStatus.tilDomene(),
                mottattTid = hendelse.mottattTid,
                vedtakstidspunkt = null,
                ansvarligBeslutter = null,
                versjon = Versjon(UUID.randomUUID().toString()),
                søknadsformat = SøknadsFormat.DIGITAL,
                sisteSaksbehandler = null,
                gjeldendeAvklaringsBehov = null,
                gjeldendeAvklaringsbehovStatus = null,
                venteÅrsak = null,
                returÅrsak = null,
                gjeldendeStegGruppe = null,
                resultat = null,
            ),
        )

        val res = BehandlingshistorikkBeregner().beregn(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.nåværendeTilstand().gjeldendeAvklaringsBehov).isNull()
    }

    @Test
    fun `beregner historikk for behandling uten avklaringsbehov`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/resendt_revurdering_automatisk.json")

        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            årsaker = listOf(),
            oppdatertTidspunkt = null,
            hendelser = listOf(),
            tilstand = BehandlingTilstand(
                status = hendelse.behandlingStatus.tilDomene(),
                mottattTid = hendelse.mottattTid,
                vedtakstidspunkt = null,
                ansvarligBeslutter = null,
                versjon = Versjon(UUID.randomUUID().toString()),
                søknadsformat = SøknadsFormat.DIGITAL,
                sisteSaksbehandler = null,
                gjeldendeAvklaringsBehov = null,
                gjeldendeAvklaringsbehovStatus = null,
                venteÅrsak = null,
                returÅrsak = null,
                gjeldendeStegGruppe = null,
                resultat = null,
            ),
        )

        val res = BehandlingshistorikkBeregner().beregn(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.hendelser).hasSize(2)

        assertThat(res.hendelser.last().tilstand.status).isEqualTo(BehandlingStatus.AVSLUTTET)
    }

    @Test
    fun `beregner historikk for avbrutt revurdering`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/avbrutt_revurdering.json")
        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            årsaker = listOf(),
            oppdatertTidspunkt = null,
            hendelser = listOf(),
            tilstand = BehandlingTilstand(
                status = hendelse.behandlingStatus.tilDomene(),
                mottattTid = hendelse.mottattTid,
                vedtakstidspunkt = null,
                ansvarligBeslutter = null,
                versjon = Versjon(UUID.randomUUID().toString()),
                søknadsformat = SøknadsFormat.DIGITAL,
                sisteSaksbehandler = null,
                gjeldendeAvklaringsBehov = null,
                gjeldendeAvklaringsbehovStatus = null,
                venteÅrsak = null,
                returÅrsak = null,
                gjeldendeStegGruppe = null,
                resultat = null,
            ),
        )

        val res = BehandlingshistorikkBeregner().beregn(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.hendelser).hasSize(7)

        val førsteHendelse = res.hendelser.first()
        val sisteHendelse = res.hendelser.last()
        assertThat(førsteHendelse.tilstand.status).isEqualTo(BehandlingStatus.OPPRETTET)
        assertThat(sisteHendelse.tilstand.status).isEqualTo(BehandlingStatus.AVSLUTTET)

        assertThat(førsteHendelse.hendelsesTidspunkt).isNotEqualTo(sisteHendelse.hendelsesTidspunkt)
    }

    @Test
    fun `beregner historikk for meldekortbehandling`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/meldekort_behandling.json")
        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            årsaker = listOf(),
            oppdatertTidspunkt = null,
            hendelser = listOf(),
            tilstand = BehandlingTilstand(
                status = hendelse.behandlingStatus.tilDomene(),
                mottattTid = hendelse.mottattTid,
                vedtakstidspunkt = null,
                ansvarligBeslutter = null,
                versjon = Versjon(UUID.randomUUID().toString()),
                søknadsformat = SøknadsFormat.DIGITAL,
                sisteSaksbehandler = null,
                gjeldendeAvklaringsBehov = null,
                gjeldendeAvklaringsbehovStatus = null,
                venteÅrsak = null,
                returÅrsak = null,
                gjeldendeStegGruppe = null,
                resultat = null,
            ),
        )

        val res = BehandlingshistorikkBeregner().beregn(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.hendelser).hasSize(2)

        val førsteHendelse = res.hendelser.first()
        val sisteHendelse = res.hendelser.last()
        assertThat(førsteHendelse.tilstand.status).isEqualTo(BehandlingStatus.OPPRETTET)
        assertThat(sisteHendelse.tilstand.status).isEqualTo(BehandlingStatus.AVSLUTTET)

        assertThat(førsteHendelse.hendelsesTidspunkt).isNotEqualTo(sisteHendelse.hendelsesTidspunkt)
    }
}

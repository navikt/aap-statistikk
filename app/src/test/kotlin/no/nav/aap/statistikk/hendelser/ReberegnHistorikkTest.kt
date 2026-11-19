package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilRetur
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.ReturÅrsakkobling
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.builders.opprettTestStoppetBehandling
import no.nav.aap.statistikk.testutils.fakes.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class ReberegnHistorikkTest {
    private val start = LocalDateTime.of(2026, 11, 19, 10, 0)

    @Test
    fun `historikken bruker behovstilstanden på hvert tidspunkt`() {
        val dto = hendelseMedReturOgVent()
        val sak = Sak(
            saksnummer = Saksnummer(dto.saksnummer),
            person = Person(ident = no.nav.aap.statistikk.person.Ident(dto.ident)),
            sakStatus = dto.sakStatus.tilDomene(),
            sistOppdatert = start,
        )
        val behandling = BehandlingService(FakeBehandlingRepository(), SkjermingService(FakePdlGateway()))
            .konstruerBehandling(dto, sak, eksisterendeBehandlingKanskje = null)

        val historikk = ReberegnHistorikk().avklaringsbehovTilHistorikk(dto, behandling).hendelser

        assertThat(historikk.map { it.hendelsesTidspunkt })
            .containsExactlyElementsOf(listOf(0L, 1L, 3L, 4L, 5L, 6L).map { start.plusMinutes(it) })
        assertThat(historikk.map { it.avklaringsBehov }).containsExactly(
            null,
            Definisjon.AVKLAR_SYKDOM,
            null,
            Definisjon.AVKLAR_SYKDOM,
            Definisjon.AVKLAR_SYKDOM,
            Definisjon.AVKLAR_SYKDOM,
        )
        assertThat(historikk.map { it.saksbehandler?.ident })
            .containsExactly(null, null, "Beslutter", "Kvalitetssikrer", "Ventebehandler", "Ventebehandler")
        assertThat(historikk.map { it.ansvarligBeslutter })
            .containsExactly(null, null, "Beslutter", null, null, null)
        assertThat(historikk.map { it.returÅrsak }).containsExactly(
            null,
            null,
            null,
            "MANGELFULL_BEGRUNNELSE",
            "MANGELFULL_BEGRUNNELSE",
            "MANGELFULL_BEGRUNNELSE",
        )
        assertThat(historikk.map { it.vedtakstidspunkt }).containsOnlyNulls()

        val påVent = historikk.single { it.hendelsesTidspunkt == start.plusMinutes(5) }
        assertThat(påVent.avklaringsbehovStatus).isEqualTo(Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER)
        assertThat(påVent.steggruppe).isEqualTo(Definisjon.AVKLAR_SYKDOM.løsesISteg.gruppe)
        assertThat(påVent.sisteLøsteAvklaringsbehov).isEqualTo(Definisjon.FATTE_VEDTAK)
        assertThat(påVent.sisteSaksbehandlerSomLøstebehov).isEqualTo("Beslutter")
        assertThat(påVent.sistLøsteAvklaringsbehovTidspunkt).isEqualTo(start.plusMinutes(3))
        assertThat(påVent.venteÅrsak).isEqualTo("VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER")
        assertThat(påVent.returÅrsakkoblinger).containsExactly(
            ReturÅrsakkobling(Definisjon.AVKLAR_SYKDOM, listOf("MANGLENDE_UTREDNING", "MANGELFULL_BEGRUNNELSE")),
        )
        assertThat(historikk.last().venteÅrsak).isNull()
        assertThat(historikk.first().status).isEqualTo(BehandlingStatus.OPPRETTET)
        assertThat(historikk.last().status).isEqualTo(BehandlingStatus.UTREDES)
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
    fun `begynne på test på reberegne historikk`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")

        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sakStatus = hendelse.sakStatus.tilDomene(),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            status = hendelse.behandlingStatus.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            mottattTid = hendelse.mottattTid,
            vedtakstidspunkt = null,
            ansvarligBeslutter = null,
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.DIGITAL,
            sisteSaksbehandler = null,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            gjeldendeAvklaringsBehov = null,
            gjeldendeAvklaringsbehovStatus = null,
            venteÅrsak = null,
            returÅrsak = null,
            gjeldendeStegGruppe = null,
            årsaker = listOf(),
            resultat = null,
            oppdatertTidspunkt = null,
            hendelser = listOf(),
        )

        val res = ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.gjeldendeAvklaringsBehov).isNull()
    }

    @Test
    fun `reberegne historikk for tom behandling`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/resendt_revurdering_automatisk.json")

        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sakStatus = hendelse.sakStatus.tilDomene(),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            status = hendelse.behandlingStatus.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            mottattTid = hendelse.mottattTid,
            vedtakstidspunkt = null,
            ansvarligBeslutter = null,
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.DIGITAL,
            sisteSaksbehandler = null,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            gjeldendeAvklaringsBehov = null,
            gjeldendeAvklaringsbehovStatus = null,
            venteÅrsak = null,
            returÅrsak = null,
            gjeldendeStegGruppe = null,
            årsaker = listOf(),
            resultat = null,
            oppdatertTidspunkt = null,
            hendelser = listOf(),
        )

        val res = ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.hendelser).hasSize(2)

        assertThat(res.hendelser.last().status).isEqualTo(BehandlingStatus.AVSLUTTET)
    }

    @Test
    fun `reberegn historikk for avbrutt revurdering`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/avbrutt_revurdering.json")
        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sakStatus = hendelse.sakStatus.tilDomene(),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            status = hendelse.behandlingStatus.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            mottattTid = hendelse.mottattTid,
            vedtakstidspunkt = null,
            ansvarligBeslutter = null,
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.DIGITAL,
            sisteSaksbehandler = null,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            gjeldendeAvklaringsBehov = null,
            gjeldendeAvklaringsbehovStatus = null,
            venteÅrsak = null,
            returÅrsak = null,
            gjeldendeStegGruppe = null,
            årsaker = listOf(),
            resultat = null,
            oppdatertTidspunkt = null,
            hendelser = listOf(),
        )

        val res = ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.hendelser).hasSize(7)

        val førsteHendelse = res.hendelser.first()
        val sisteHendelse = res.hendelser.last()
        assertThat(førsteHendelse.status).isEqualTo(BehandlingStatus.OPPRETTET)
        assertThat(sisteHendelse.status).isEqualTo(BehandlingStatus.AVSLUTTET)

        assertThat(førsteHendelse.hendelsesTidspunkt).isNotEqualTo(sisteHendelse.hendelsesTidspunkt)
    }

    @Test
    fun `meldekort-behandling`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/meldekort_behandling.json")
        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = no.nav.aap.statistikk.person.Ident(hendelse.ident)),
                sakStatus = hendelse.sakStatus.tilDomene(),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            status = hendelse.behandlingStatus.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            mottattTid = hendelse.mottattTid,
            vedtakstidspunkt = null,
            ansvarligBeslutter = null,
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.DIGITAL,
            sisteSaksbehandler = null,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            gjeldendeAvklaringsBehov = null,
            gjeldendeAvklaringsbehovStatus = null,
            venteÅrsak = null,
            returÅrsak = null,
            gjeldendeStegGruppe = null,
            årsaker = listOf(),
            resultat = null,
            oppdatertTidspunkt = null,
            hendelser = listOf(),
        )

        val res = ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.hendelser).hasSize(2)

        val førsteHendelse = res.hendelser.first()
        val sisteHendelse = res.hendelser.last()
        assertThat(førsteHendelse.status).isEqualTo(BehandlingStatus.OPPRETTET)
        assertThat(sisteHendelse.status).isEqualTo(BehandlingStatus.AVSLUTTET)

        assertThat(førsteHendelse.hendelsesTidspunkt).isNotEqualTo(sisteHendelse.hendelsesTidspunkt)
    }
}

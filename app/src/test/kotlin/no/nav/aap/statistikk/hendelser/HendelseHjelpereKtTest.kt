package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.*
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.statistikk.behandling.BehandlingStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as EndringStatus


@ExtendWith(SoftAssertionsExtension::class)
class HendelseHjelpereKtTest {
    companion object {
        private fun fromResources(filnavn: String): String {
            return object {}.javaClass.getResource("/$filnavn")?.readText()!!
        }
    }


    @Test
    fun `midt i behandling`(softly: SoftAssertions) {
        val hendelserString = fromResources("avklaringsbehovhendelser/grunnlag_steg.json")
        val stoppetBehandling =
            DefaultJsonMapper.fromJson<StoppetBehandling>(hendelserString)
        val hendelser = stoppetBehandling.avklaringsbehov

        softly.apply {
            assertThat(hendelser).isNotEmpty()
            assertThat(hendelser.utledAnsvarligBeslutter()).isNull()
            assertThat(hendelser.sistePersonPåBehandling()).isEqualTo("VEILEDER")
            assertThat(hendelser.utledVedtakTid()).isNull()
            assertThat(hendelser.årsakTilRetur()).isNull()
            assertThat(hendelser.utledBehandlingStatus()).isEqualTo(BehandlingStatus.UTREDES)
            assertThat(hendelser.utledGjeldendeAvklaringsBehov()).isEqualTo(
                FASTSETT_BEREGNINGSTIDSPUNKT
            )
            assertThat(hendelser.sisteAvklaringsbehovStatus()).isEqualTo(EndringStatus.OPPRETTET)
            assertThat(hendelser.utledGjeldendeStegType()).isEqualTo(StegType.FASTSETT_BEREGNINGSTIDSPUNKT)
            assertThat(hendelser.utledÅrsakTilSattPåVent()).isNull()
        }
    }

    @Test
    fun `sendt tilbake fra beslutter 11-5`(softly: SoftAssertions) {
        val hendelserString =
            fromResources("avklaringsbehovhendelser/sendt_tilbake_11_5_fra_beslutter.json")
        val stoppetBehandling =
            DefaultJsonMapper.fromJson<StoppetBehandling>(hendelserString)
        val hendelser = stoppetBehandling.avklaringsbehov

        softly.apply {
            assertThat(hendelser).isNotEmpty()
            assertThat(hendelser.utledAnsvarligBeslutter()).isNull()
            assertThat(hendelser.sistePersonPåBehandling()).isEqualTo("VEILEDER")
            assertThat(hendelser.utledVedtakTid()).isNull()
            assertThat(hendelser.årsakTilRetur()).describedAs("Årsak til retur").isEqualTo(
                ÅrsakTilReturKode.MANGLENDE_UTREDNING
            )
            assertThat(hendelser.utledBehandlingStatus()).isEqualTo(BehandlingStatus.UTREDES)
            assertThat(hendelser.utledGjeldendeAvklaringsBehov()).isEqualTo(
                AVKLAR_SYKDOM
            )
            assertThat(hendelser.sisteAvklaringsbehovStatus()).isEqualTo(EndringStatus.SENDT_TILBAKE_FRA_BESLUTTER)
            assertThat(hendelser.utledGjeldendeStegType()).isEqualTo(StegType.AVKLAR_SYKDOM)
            assertThat(hendelser.utledÅrsakTilSattPåVent()).isNull()
        }
    }

    @Test
    fun `er på brevsteget`(softly: SoftAssertions) {
        val hendelserString =
            fromResources("avklaringsbehovhendelser/er_pa_brev_steget.json")
        val stoppetBehandling =
            DefaultJsonMapper.fromJson<StoppetBehandling>(hendelserString)
        val hendelser = stoppetBehandling.avklaringsbehov

        softly.apply {
            assertThat(hendelser).isNotEmpty()
            assertThat(hendelser.utledAnsvarligBeslutter()).isEqualTo("VEILEDER")
            assertThat(hendelser.sistePersonPåBehandling()).isEqualTo("VEILEDER")
            assertThat(hendelser.utledVedtakTid()).isEqualTo(LocalDateTime.parse("2025-09-24T13:53:01.368"))
            assertThat(hendelser.årsakTilRetur()).describedAs("Årsak til retur").isNull()
            assertThat(hendelser.utledBehandlingStatus()).isEqualTo(BehandlingStatus.IVERKSETTES)
            assertThat(hendelser.utledGjeldendeAvklaringsBehov()).isEqualTo(
                SKRIV_VEDTAKSBREV
            )
            assertThat(hendelser.sisteAvklaringsbehovStatus()).isEqualTo(EndringStatus.OPPRETTET)
            assertThat(hendelser.utledGjeldendeStegType()).isEqualTo(StegType.BREV)
            assertThat(hendelser.utledÅrsakTilSattPåVent()).isNull()
        }
    }

    @Test
    fun `fullført førstegangsbehandling`(softly: SoftAssertions) {
        val hendelserString =
            fromResources("avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")
        val stoppetBehandling =
            DefaultJsonMapper.fromJson<StoppetBehandling>(hendelserString)
        val hendelser = stoppetBehandling.avklaringsbehov

        softly.apply {
            assertThat(hendelser).isNotEmpty()
            assertThat(hendelser.utledAnsvarligBeslutter()).isEqualTo("VEILEDER")
            assertThat(hendelser.sistePersonPåBehandling()).isEqualTo("VEILEDER")
            assertThat(hendelser.utledVedtakTid()).isEqualTo(LocalDateTime.parse("2025-09-24T13:53:01.368"))
            assertThat(hendelser.årsakTilRetur()).describedAs("Årsak til retur").isNull()
            assertThat(hendelser.utledBehandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
            assertThat(hendelser.utledGjeldendeAvklaringsBehov()).isEqualTo(
                null
            )
            assertThat(hendelser.sisteAvklaringsbehovStatus()).isNull()
            assertThat(hendelser.utledGjeldendeStegType()).isNull()
            assertThat(hendelser.utledÅrsakTilSattPåVent()).isNull()
        }
    }

    @Test
    fun `kan utlede vedtaktid fra liste av avklaringsbehovhendelser`() {
        val utledetVedtakTid = avklaringsbehovHendelser.utledVedtakTid()

        assertThat(utledetVedtakTid).isEqualTo(LocalDateTime.parse("2024-10-18T11:12:01.293"))
    }

    @Test
    fun `gir null på tom liste`() {
        assertThat(emptyList<AvklaringsbehovHendelseDto>().utledVedtakTid()).isNull()
    }

    @Test
    fun `kan hente ut siste person som jobbet på en behandling fra en hendelse`() {
        assertThat(avklaringsbehovHendelser.sistePersonPåBehandling()).isNotNull()
        assertThat(avklaringsbehovHendelser.sistePersonPåBehandling()).isEqualTo("Z9945700")
    }

    @Test
    fun `siste person på behandling - tester på en ufullført behandling, er ikke Kelvin`() {
        assertThat(ufullførtBehandlingEndringer.sistePersonPåBehandling()).isNotNull()
        assertThat(ufullførtBehandlingEndringer.sistePersonPåBehandling()).isNotEqualTo("Kelvin")
        assertThat(ufullførtBehandlingEndringer.sistePersonPåBehandling()).isEqualTo("Z99400")
    }

    @Test
    fun `utled årsak til retur`() {
        assertThat(returHendelser.årsakTilRetur()).isEqualTo(ÅrsakTilReturKode.MANGLENDE_UTREDNING)
    }

    @Test
    fun `ekte data, verifiser`() {
        assertThat(ekte2.årsakTilRetur()).isEqualTo(ÅrsakTilReturKode.ANNET)
        assertThat(ekte2.sisteAvklaringsbehovStatus()).isEqualTo(Status.SENDT_TILBAKE_FRA_BESLUTTER)
    }

    @Test
    fun `utled gjeldendde stegtype fra ekte data`() {
        assertThat(ekte2.utledGjeldendeStegType()).isEqualTo(StegType.AVKLAR_SYKDOM)
    }

    @Test
    fun `utled gjeldende avklaringsbehov ekte data`() {
        assertThat(ekte2.utledGjeldendeAvklaringsBehov()).isEqualTo(
            AVKLAR_SYKDOM
        )
    }

    @Test
    fun `utled årsak til retur ekte`() {
        assertThat(ekteEksempel.årsakTilRetur()).isEqualTo(ÅrsakTilReturKode.MANGELFULL_BEGRUNNELSE)
        assertThat(ekteEksempel.sisteAvklaringsbehovStatus()).isEqualTo(Status.SENDT_TILBAKE_FRA_BESLUTTER)

        assertThat(ekteEksempel.utledGjeldendeStegType()).isEqualTo(StegType.VURDER_BISTANDSBEHOV)
    }

    @Test
    fun `om behandlingen nettopp er opprettet, er det ingen menneskelige saksbehandlere`() {
        val liste = listOf(
            AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = AVKLAR_STUDENT,
                status = EndringStatus.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = EndringStatus.OPPRETTET,
                        tidsstempel = LocalDateTime.of(2024, 10, 23, 9, 30, 7),
                        frist = null,
                        endretAv = "Kelvin"
                    )
                )
            )
        )
        assertThat(liste.sistePersonPåBehandling()).isNull()
    }


    @Test
    fun `kan utlede gjeldende stegtype`() {
        assertThat(ufullførtBehandlingEndringer.utledGjeldendeStegType()).isEqualTo(
            StegType.FATTE_VEDTAK
        )
    }

    @Test
    fun `utled gjeldende avklaringsbehov`() {
        assertThat(ufullførtBehandlingEndringer.utledGjeldendeAvklaringsBehov())
            .isEqualTo(FATTE_VEDTAK)
    }

    @Test
    fun `gjeldende avklaringsbehov er null for avsluttet behandling`() {
        assertThat(avklaringsbehovHendelser.utledGjeldendeAvklaringsBehov()).isNull()
    }

    @Test
    fun `ansvarlig beslutter kan hentes ut`() {
        assertThat(avklaringsbehovHendelser.utledAnsvarligBeslutter()).isEqualTo("Z9945700")
    }

    @Test
    fun `ansvarlig beslutter er null om avklaringsbehovet ikke er avsluttet`() {
        val hendelser = listOf(
            AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = FATTE_VEDTAK,
                status = Status.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = Status.OPPRETTET,
                        tidsstempel = LocalDateTime.parse("2025-04-08T09:03:54.371"),
                        endretAv = "Kelvin",
                    ),
                    EndringDTO(
                        status = Status.AVSLUTTET,
                        tidsstempel = LocalDateTime.parse("2025-04-08T09:04:14.935"),
                        endretAv = "Z994573",
                    ),
                    EndringDTO(
                        status = Status.OPPRETTET,
                        tidsstempel = LocalDateTime.parse("2025-04-08T09:04:15.080"),
                        endretAv = "Kelvin",
                    ),
                ),
            )
        )

        assertThat(hendelser.utledAnsvarligBeslutter()).isNull()
    }

    @Test
    fun `ansvarlig beslutter er ikke-null om avklaringsbehovet er avsluttet`() {
        val hendelser = listOf(
            AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = FATTE_VEDTAK,
                status = Status.AVSLUTTET,
                endringer = listOf(
                    EndringDTO(
                        status = Status.OPPRETTET,
                        tidsstempel = LocalDateTime.parse("2025-04-08T09:03:54.371"),
                        endretAv = "Kelvin",
                    ),
                    EndringDTO(
                        status = Status.AVSLUTTET,
                        tidsstempel = LocalDateTime.parse("2025-04-08T09:04:14.935"),
                        endretAv = "Z994573",
                    ),
                ),
            )
        )

        assertThat(hendelser.utledAnsvarligBeslutter()).isNotNull()
        assertThat(hendelser.utledAnsvarligBeslutter()).isEqualTo("Z994573")
    }

    @Test
    fun `å utlede venteårsak gir null om ingen er gitt`() {
        assertThat(avklaringsbehovHendelser.utledÅrsakTilSattPåVent()).isNull()
    }

    @Test
    fun `utleder nyeste venteårsak`() {
        val input: List<AvklaringsbehovHendelseDto> =
            listOf<AvklaringsbehovHendelseDto>() + AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = MANUELT_SATT_PÅ_VENT,
                status = Status.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = Status.OPPRETTET,
                        tidsstempel = LocalDateTime.now(),
                        endretAv = "meg",
                        årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER
                    )
                )
            ) + AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = FASTSETT_BEREGNINGSTIDSPUNKT,
                status = Status.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = Status.OPPRETTET,
                        tidsstempel = LocalDateTime.now().minusDays(1),
                        endretAv = "meg",
                        årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_MASKINELL_AVKLARING
                    )
                )
            )

        assertThat(input.utledÅrsakTilSattPåVent()).isEqualTo("VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER")
    }
}

val returHendelser = listOf(
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
        status = EndringStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:42:07.925"),
                frist = null,
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                årsakTilRetur = listOf(ÅrsakTilRetur(ÅrsakTilReturKode.MANGLENDE_UTREDNING)),
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:45.371"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    )
)

val avklaringsbehovHendelser = listOf(
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:42:07.925"),
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:18.400"),
                endretAv = "Z994573"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:45.371"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FRITAK_MELDEPLIKT,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:58:51.113"),
                endretAv = "Z994573"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:58:51.172"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:18.957"),
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:01:05.396"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FASTSETT_BEREGNINGSTIDSPUNKT,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:04:09.241"),
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:14.231"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = KVALITETSSIKRING,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:01:05.883"),
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:04:08.355"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FORESLÅ_VEDTAK,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:17.882"),
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:27.634"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FATTE_VEDTAK,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:28.821"),
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:12:01.293"),
                endretAv = "Z9945700"
            )
        )
    )
)

val ufullførtBehandlingEndringer = listOf(
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:05:59.373"),
                frist = null,
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:39.461"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FASTSETT_ARBEIDSEVNE,
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:50:40.989"),
                frist = null,
                endretAv = "Z994573"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:50:41.070"),
                frist = null,
                endretAv = "Z994573"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:00.104"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FRITAK_MELDEPLIKT,
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:55.558"),
                frist = null,
                endretAv = "Z994573"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:55.607"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:41.492"),
                frist = null,
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:07.726"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = KVALITETSSIKRING,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:09.136"),
                frist = null,
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:21.191"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FORESLÅ_VEDTAK,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:27.150"),
                frist = null,
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:51.815"),
                frist = null,
                endretAv = "Z99400"
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FATTE_VEDTAK,
        status = EndringStatus.OPPRETTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:54.928"),
                frist = null,
                endretAv = "Kelvin"
            )
        )
    )
)

val sattPåVentPåKvalitetssikringNAYSteg = listOf(
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T12:44:55.429"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:23:45.145"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:24:42.586"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:24:59.272"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:23:47.028"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:24:02.705"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:24:42.598"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:25:06.731"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = KVALITETSSIKRING,
        status = EndringStatus.OPPRETTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:24:04.014"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:24:42.608"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T13:25:07.826"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = BESTILL_LEGEERKLÆRING,
        status = EndringStatus.OPPRETTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-16T15:35:53.935"),
                frist = LocalDate.parse("2025-02-13"),
                endretAv = "Z994573",
                årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_MEDISINSKE_OPPLYSNINGER
            )
        )
    )
)

val medflere = listOf(
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_STUDENT,
        status = Status.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-12-16T13:49:08.580"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:27:34.783"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.459"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:27:35.151"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:59:00.982"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:59:54.186"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T14:04:30.185"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:51:10.818"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = Status.KVALITETSSIKRET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:51:24.490"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.467"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:59:01.673"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:59:18.666"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:59:54.196"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:51:15.631"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = Status.KVALITETSSIKRET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:51:24.496"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.476"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FASTSETT_BEREGNINGSTIDSPUNKT,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:52:20.688"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:54:17.208"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.494"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_BARNETILLEGG,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:55:15.794"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:57:29.767"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.512"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_SONINGSFORRHOLD,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:57:32.760"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:58:22.622"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.522"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_YRKESSKADE,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:51:25.608"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:52:19.329"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.485"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FASTSETT_YRKESSKADEINNTEKT,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:54:18.458"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:55:11.476"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = Status.AVBRUTT,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:55:14.658"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.TOTRINNS_VURDERT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.502"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = KVALITETSSIKRING,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:59:19.189"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-10T13:59:54.206"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:51:16.425"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:51:24.503"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FORESLÅ_VEDTAK,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T08:58:25.607"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:10:21.532"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = Status.AVBRUTT,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:10:58.257"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:10:58.276"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = FATTE_VEDTAK,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:10:24.193"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.531"),
                frist = null,
                endretAv = "Z994553",
                årsakTilSattPåVent = null
            )
        )
    ),
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = BESTILL_BREV,
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:13.770"),
                frist = LocalDate.parse("2025-01-22"),
                endretAv = "Kelvin",
                årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_MASKINELL_AVKLARING
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-01-21T09:13:14.902"),
                frist = null,
                endretAv = "Brevløsning",
                årsakTilSattPåVent = null
            )
        )
    )
)


val ekteEksempel = listOf(
    AvklaringsbehovHendelseDto(
        avklaringsbehovDefinisjon = AVKLAR_BISTANDSBEHOV,
        status = Status.SENDT_TILBAKE_FRA_BESLUTTER,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T12:46:13.515"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T12:46:23.688"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T12:46:37.237"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T12:47:01.276"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T12:47:27.891"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T12:47:39.533"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:05:17.706"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:05:26.511"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:07:59.934"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.KVALITETSSIKRET,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:08:09.089"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:10:18.716"),
                frist = null,
                endretAv = "Kelvin",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:24:44.463"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:26:02.921"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = listOf(ÅrsakTilRetur(årsak = ÅrsakTilReturKode.MANGELFULL_BEGRUNNELSE))
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:26:15.263"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = emptyList()
            ),
            EndringDTO(
                status = EndringStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                tidsstempel = LocalDateTime.parse("2025-04-15T13:26:59.746"),
                frist = null,
                endretAv = "Z994573",
                årsakTilSattPåVent = null,
                årsakTilRetur = listOf(ÅrsakTilRetur(årsak = ÅrsakTilReturKode.MANGELFULL_BEGRUNNELSE))
            )
        ),
        typeBrev = null
    )
)

@Language("JSON")
val ekte2 = """
    [
      {
        "avklaringsbehovDefinisjon": {
          "kode": "5003",
          "type": "MANUELT_PÅKREVD",
          "løsesISteg": "AVKLAR_SYKDOM",
          "kreverToTrinn": true,
          "kvalitetssikres": true,
          "løsesAv": [
            "SAKSBEHANDLER_OPPFOLGING"
          ]
        },
        "status": "SENDT_TILBAKE_FRA_BESLUTTER",
        "endringer": [
          {
            "status": "OPPRETTET",
            "tidsstempel": "2025-04-15T12:45:18.34",
            "frist": null,
            "endretAv": "Kelvin",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T12:46:13.213",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "SENDT_TILBAKE_FRA_KVALITETSSIKRER",
            "tidsstempel": "2025-04-15T12:47:01.268",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": [
              {
                "årsak": "MANGLENDE_UTREDNING"
              }
            ]
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T12:47:18.256",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "SENDT_TILBAKE_FRA_KVALITETSSIKRER",
            "tidsstempel": "2025-04-15T12:47:39.525",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": [
              {
                "årsak": "MANGLENDE_UTREDNING"
              }
            ]
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T13:05:09.137",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "SENDT_TILBAKE_FRA_KVALITETSSIKRER",
            "tidsstempel": "2025-04-15T13:05:26.495",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": [
              {
                "årsak": "MANGLENDE_UTREDNING"
              }
            ]
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T13:07:55.93",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "KVALITETSSIKRET",
            "tidsstempel": "2025-04-15T13:08:09.083",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "SENDT_TILBAKE_FRA_BESLUTTER",
            "tidsstempel": "2025-04-15T13:10:18.705",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": [
              {
                "årsak": "ANNET"
              }
            ]
          }
        ],
        "typeBrev": null
      },
      {
        "avklaringsbehovDefinisjon": {
          "kode": "5006",
          "type": "MANUELT_PÅKREVD",
          "løsesISteg": "VURDER_BISTANDSBEHOV",
          "kreverToTrinn": true,
          "kvalitetssikres": true,
          "løsesAv": [
            "SAKSBEHANDLER_OPPFOLGING"
          ]
        },
        "status": "OPPRETTET",
        "endringer": [
          {
            "status": "OPPRETTET",
            "tidsstempel": "2025-04-15T12:46:13.515",
            "frist": null,
            "endretAv": "Kelvin",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T12:46:23.688",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T12:46:37.237",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "OPPRETTET",
            "tidsstempel": "2025-04-15T12:47:01.276",
            "frist": null,
            "endretAv": "Kelvin",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T12:47:27.891",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "OPPRETTET",
            "tidsstempel": "2025-04-15T12:47:39.533",
            "frist": null,
            "endretAv": "Kelvin",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T13:05:17.706",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "OPPRETTET",
            "tidsstempel": "2025-04-15T13:05:26.511",
            "frist": null,
            "endretAv": "Kelvin",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "AVSLUTTET",
            "tidsstempel": "2025-04-15T13:07:59.934",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
            "årsakTilRetur": []
          },
          {
            "status": "KVALITETSSIKRET",
            "tidsstempel": "2025-04-15T13:08:09.089",
            "frist": null,
            "endretAv": "Z994573",
            "årsakTilSattPåVent": null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:10:18.716",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    }, {
      "avklaringsbehovDefinisjon" : {
        "kode" : "5008",
        "type" : "MANUELT_PÅKREVD",
        "løsesISteg" : "FASTSETT_BEREGNINGSTIDSPUNKT",
        "kreverToTrinn" : true,
        "kvalitetssikres" : false,
        "løsesAv" : [ "SAKSBEHANDLER_NASJONAL" ]
      },
      "status" : "OPPRETTET",
      "endringer" : [ {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:08:09.599",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T13:08:22.652",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:10:18.721",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    }, {
      "avklaringsbehovDefinisjon" : {
        "kode" : "5017",
        "type" : "MANUELT_PÅKREVD",
        "løsesISteg" : "VURDER_LOVVALG",
        "kreverToTrinn" : true,
        "kvalitetssikres" : false,
        "løsesAv" : [ "SAKSBEHANDLER_NASJONAL" ]
      },
      "status" : "TOTRINNS_VURDERT",
      "endringer" : [ {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T12:44:23.335",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T12:45:17.282",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "TOTRINNS_VURDERT",
        "tidsstempel" : "2025-04-15T13:10:18.694",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    }, {
      "avklaringsbehovDefinisjon" : {
        "kode" : "5020",
        "type" : "MANUELT_PÅKREVD",
        "løsesISteg" : "VURDER_MEDLEMSKAP",
        "kreverToTrinn" : true,
        "kvalitetssikres" : false,
        "løsesAv" : [ "SAKSBEHANDLER_NASJONAL" ]
      },
      "status" : "OPPRETTET",
      "endringer" : [ {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:08:25.949",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T13:09:20.97",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:10:18.729",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    }, {
      "avklaringsbehovDefinisjon" : {
        "kode" : "5026",
        "type" : "MANUELT_PÅKREVD",
        "løsesISteg" : "REFUSJON_KRAV",
        "kreverToTrinn" : false,
        "kvalitetssikres" : false,
        "løsesAv" : [ "SAKSBEHANDLER_OPPFOLGING" ]
      },
      "status" : "OPPRETTET",
      "endringer" : [ {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T12:46:23.943",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T12:46:41.824",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:10:18.738",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    }, {
      "avklaringsbehovDefinisjon" : {
        "kode" : "5097",
        "type" : "MANUELT_PÅKREVD",
        "løsesISteg" : "KVALITETSSIKRING",
        "kreverToTrinn" : false,
        "kvalitetssikres" : false,
        "løsesAv" : [ "KVALITETSSIKRER" ]
      },
      "status" : "AVSLUTTET",
      "endringer" : [ {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T12:46:42.028",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T12:47:01.283",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T12:47:28.133",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T12:47:39.54",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:05:18.067",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T13:05:26.518",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:08:00.257",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T13:08:09.095",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    }, {
      "avklaringsbehovDefinisjon" : {
        "kode" : "5098",
        "type" : "MANUELT_PÅKREVD",
        "løsesISteg" : "FORESLÅ_VEDTAK",
        "kreverToTrinn" : false,
        "kvalitetssikres" : false,
        "løsesAv" : [ "SAKSBEHANDLER_NASJONAL" ]
      },
      "status" : "AVBRUTT",
      "endringer" : [ {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:09:23.561",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T13:09:59.194",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVBRUTT",
        "tidsstempel" : "2025-04-15T13:23:49.498",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    }, {
      "avklaringsbehovDefinisjon" : {
        "kode" : "5099",
        "type" : "MANUELT_PÅKREVD",
        "løsesISteg" : "FATTE_VEDTAK",
        "kreverToTrinn" : false,
        "kvalitetssikres" : false,
        "løsesAv" : [ "BESLUTTER" ]
      },
      "status" : "AVSLUTTET",
      "endringer" : [ {
        "status" : "OPPRETTET",
        "tidsstempel" : "2025-04-15T13:09:59.548",
        "frist" : null,
        "endretAv" : "Kelvin",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      }, {
        "status" : "AVSLUTTET",
        "tidsstempel" : "2025-04-15T13:10:18.747",
        "frist" : null,
        "endretAv" : "Z994573",
        "årsakTilSattPåVent" : null,
        "årsakTilRetur" : [ ]
      } ],
      "typeBrev" : null
    } ]
""".trimIndent().let { DefaultJsonMapper.fromJson<List<AvklaringsbehovHendelseDto>>(it) }
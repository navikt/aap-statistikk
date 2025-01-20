package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.Rolle
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as EndringStatus

class HendelseHjelpereKtTest {
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
    fun `tester på en ufullført behandling, er ikke Kelvin`() {
        assertThat(ufullførtBehandlingEndringer.sistePersonPåBehandling()).isNotNull()
        assertThat(ufullførtBehandlingEndringer.sistePersonPåBehandling()).isNotEqualTo("Kelvin")
        assertThat(ufullførtBehandlingEndringer.sistePersonPåBehandling()).isEqualTo("Z99400")
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
        assertThat(ufullførtBehandlingEndringer.utledGjeldendeAvklaringsBehov()).isEqualTo(
            AvklaringsbehovKode.`5099`.toString()
        )
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

    @Test
    fun `er hos NAY`() {
        assertThat(sattPåVentPåNAYSteg.hosNayEllerIkke()).isTrue()
    }

    @Test
    fun `er ikke hos NAY, men sykdomssteget`() {
        val hosSykdomsSteget = listOf(
            AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
                status = EndringStatus.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = EndringStatus.OPPRETTET,
                        tidsstempel = LocalDateTime.parse("2024-10-18T10:42:07.925"),
                        frist = null,
                        endretAv = "Kelvin"
                    )
                )
            )
        )

        assertThat(hosSykdomsSteget.hosNayEllerIkke()).isFalse()
    }
}

val avklaringsbehovHendelser = listOf(
    AvklaringsbehovHendelseDto(

        avklaringsbehovDefinisjon = AVKLAR_SYKDOM,
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            EndringDTO(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:42:07.925"),
                frist = null,
                endretAv = "Kelvin"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:18.400"),
                frist = null,
                endretAv = "Z994573"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:45.371"),
                frist = null,
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
                frist = null,
                endretAv = "Z994573"
            ),
            EndringDTO(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:58:51.172"),
                frist = null,
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

val sattPåVentPåNAYSteg = listOf(
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

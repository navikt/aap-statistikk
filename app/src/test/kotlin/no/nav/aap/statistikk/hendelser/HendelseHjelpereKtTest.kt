package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.DefinisjonDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        assertThat(avklaringsbehovHendelser.sistePersonPåBehandling()).isEqualTo("Z994573")
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
                definisjon = DefinisjonDTO(
                    type = AvklaringsbehovKode.`5001`,
                    behovType = BehovType.MANUELT_PÅKREVD,
                    løsesISteg = StegType.AVKLAR_STUDENT
                ),
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
    fun `utled gjeldende avklaringsbehov`() {
        assertThat(ufullførtBehandlingEndringer.utledGjeldendeAvklaringsBehov()).isEqualTo(
            AvklaringsbehovKode.`5099`.toString()
        )
    }

    @Test
    fun `returnerer null for avsluttet behandling`() {
        assertThat(avklaringsbehovHendelser.utledGjeldendeAvklaringsBehov()).isNull()
    }

    @Test
    fun `å utlede venteårsak gir null om ingen er gitt`() {
        assertThat(avklaringsbehovHendelser.utledÅrsakTilSattPåVent()).isNull()
    }

    @Test
    fun `utleder nyeste venteårsak`() {
        val input: List<AvklaringsbehovHendelseDto> =
            listOf<AvklaringsbehovHendelseDto>() + AvklaringsbehovHendelseDto(
                definisjon = DefinisjonDTO(
                    type = AvklaringsbehovKode.`9001`,
                    behovType = BehovType.VENTEPUNKT,
                    løsesISteg = StegType.AVKLAR_SYKDOM
                ),
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
                definisjon = DefinisjonDTO(
                    type = AvklaringsbehovKode.`9001`,
                    behovType = BehovType.VENTEPUNKT,
                    løsesISteg = StegType.BEREGN_TILKJENT_YTELSE
                ),
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

val avklaringsbehovHendelser = listOf(
    AvklaringsbehovHendelseDto(
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5003`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_SYKDOM
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5005`,
            behovType = BehovType.MANUELT_FRIVILLIG,
            løsesISteg = StegType.FRITAK_MELDEPLIKT
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5006`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.VURDER_BISTANDSBEHOV
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5008`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FASTSETT_BEREGNINGSTIDSPUNKT
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5097`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.KVALITETSSIKRING
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5098`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FORESLÅ_VEDTAK
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5099`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FATTE_VEDTAK
        ),
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
                endretAv = "Z994573"
            )
        )
    )
)

val ufullførtBehandlingEndringer = listOf(
    AvklaringsbehovHendelseDto(
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5003`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_SYKDOM
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5004`,
            behovType = BehovType.MANUELT_FRIVILLIG,
            løsesISteg = StegType.FASTSETT_ARBEIDSEVNE
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5005`,
            behovType = BehovType.MANUELT_FRIVILLIG,
            løsesISteg = StegType.FRITAK_MELDEPLIKT
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5006`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.VURDER_BISTANDSBEHOV
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5097`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.KVALITETSSIKRING
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5098`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FORESLÅ_VEDTAK
        ),
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
        definisjon = DefinisjonDTO(
            type = AvklaringsbehovKode.`5099`,
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FATTE_VEDTAK
        ),
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


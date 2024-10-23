package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.api_kontrakt.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class HendelseHjelpereKtTest {
    @Test
    fun `kan utlede vedtaktid fra liste av avklaringsbehovhendelser`() {
        val utledetVedtakTid = utledVedtakTid(avklaringsbehovHendelser)

        assertThat(utledetVedtakTid).isEqualTo(LocalDateTime.parse("2024-10-18T11:12:01.293"))
    }

    @Test
    fun `krasjer ikke på manglende hendelse`() {
        val utledetVedtakTid = utledVedtakTid(emptyList())
    }
}

val avklaringsbehovHendelser = listOf(
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5003",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_SYKDOM
        ),
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:42:07.925"),
                frist = null,
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:18.400"),
                frist = null,
                endretAv = "Z994573"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:45.371"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5005",
            behovType = BehovType.MANUELT_FRIVILLIG,
            løsesISteg = StegType.FRITAK_MELDEPLIKT
        ),
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:58:51.113"),
                frist = null,
                endretAv = "Z994573"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:58:51.172"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5006",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.VURDER_BISTANDSBEHOV
        ),
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T10:53:18.957"),
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:01:05.396"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5008",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FASTSETT_BEREGNINGSTIDSPUNKT
        ),
        status = EndringStatus.TOTRINNS_VURDERT,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:04:09.241"),
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:14.231"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5097",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.KVALITETSSIKRING
        ),
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:01:05.883"),
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:04:08.355"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5098",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FORESLÅ_VEDTAK
        ),
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:17.882"),
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:27.634"),
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5099",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FATTE_VEDTAK
        ),
        status = EndringStatus.AVSLUTTET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:07:28.821"),
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-18T11:12:01.293"),
                endretAv = "Z994573"
            )
        )
    )
)
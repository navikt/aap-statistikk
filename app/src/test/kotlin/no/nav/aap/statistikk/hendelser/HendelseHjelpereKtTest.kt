package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class HendelseHjelpereKtTest {
    @Test
    fun `kan utlede vedtaktid fra liste av avklaringsbehovhendelser`() {
        val utledetVedtakTid = avklaringsbehovHendelser.utledVedtakTid()

        assertThat(utledetVedtakTid).isEqualTo(LocalDateTime.parse("2024-10-18T11:12:01.293"))
    }

    @Test
    fun `gir null på tom liste`() {
        assertThat(emptyList<AvklaringsbehovHendelse>().utledVedtakTid()).isNull()
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
            AvklaringsbehovHendelse(
                definisjon = Definisjon(
                    type = "5001",
                    behovType = BehovType.MANUELT_PÅKREVD,
                    løsesISteg = StegType.AVKLAR_STUDENT
                ),
                status = EndringStatus.OPPRETTET,
                endringer = listOf(
                    Endring(
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
        assertThat(ufullførtBehandlingEndringer.utledGjeldendeAvklaringsBehov()).isEqualTo("5099")
    }

    @Test
    fun `returnerer null for avsluttet behandling`() {
        assertThat(avklaringsbehovHendelser.utledGjeldendeAvklaringsBehov()).isNull()
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

val ufullførtBehandlingEndringer = listOf(
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5003",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.AVKLAR_SYKDOM
        ),
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:05:59.373"),
                frist = null,
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:39.461"),
                frist = null,
                endretAv = "Z994573"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5004",
            behovType = BehovType.MANUELT_FRIVILLIG,
            løsesISteg = StegType.FASTSETT_ARBEIDSEVNE
        ),
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:50:40.989"),
                frist = null,
                endretAv = "Z994573"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:50:41.070"),
                frist = null,
                endretAv = "Z994573"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:00.104"),
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
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:55.558"),
                frist = null,
                endretAv = "Z994573"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:55.607"),
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
        status = EndringStatus.KVALITETSSIKRET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:12:41.492"),
                frist = null,
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:07.726"),
                frist = null,
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
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:09.136"),
                frist = null,
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:21.191"),
                frist = null,
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
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:27.150"),
                frist = null,
                endretAv = "Kelvin"
            ),
            Endring(
                status = EndringStatus.AVSLUTTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:51.815"),
                frist = null,
                endretAv = "Z99400"
            )
        )
    ),
    AvklaringsbehovHendelse(
        definisjon = Definisjon(
            type = "5099",
            behovType = BehovType.MANUELT_PÅKREVD,
            løsesISteg = StegType.FATTE_VEDTAK
        ),
        status = EndringStatus.OPPRETTET,
        endringer = listOf(
            Endring(
                status = EndringStatus.OPPRETTET,
                tidsstempel = LocalDateTime.parse("2024-10-28T09:51:54.928"),
                frist = null,
                endretAv = "Kelvin"
            )
        )
    )
)


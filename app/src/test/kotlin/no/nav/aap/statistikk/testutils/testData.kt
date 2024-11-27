package no.nav.aap.statistikk.testutils

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.DefinisjonDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as EndringStatus
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.*
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


fun avsluttetBehandlingDTO(referanse: UUID, saksnummer: String): AvsluttetBehandlingDTO {
    return AvsluttetBehandlingDTO(
        behandlingsReferanse = referanse,
        saksnummer = saksnummer,
        tilkjentYtelse = TilkjentYtelseDTO(
            perioder = listOf(
                TilkjentYtelsePeriodeDTO(
                    fraDato = LocalDate.now().minusYears(1),
                    tilDato = LocalDate.now().plusDays(1),
                    dagsats = 1337.420,
                    gradering = 90.0
                ),
                TilkjentYtelsePeriodeDTO(
                    fraDato = LocalDate.now().minusYears(3),
                    tilDato = LocalDate.now().minusYears(2),
                    dagsats = 1234.0,
                    gradering = 45.0
                )
            )
        ),
        vilkårsResultat = VilkårsResultatDTO(
            typeBehandling = "førstegangsbehandling",
            vilkår = listOf(
                VilkårDTO(
                    vilkårType = Vilkårtype.ALDERSVILKÅRET, perioder = listOf(
                        VilkårsPeriodeDTO(
                            fraDato = LocalDate.now().minusYears(2),
                            tilDato = LocalDate.now().plusDays(3),
                            manuellVurdering = false,
                            utfall = Utfall.OPPFYLT
                        )
                    )
                )
            )
        ),
        beregningsGrunnlag = BeregningsgrunnlagDTO(
            grunnlagYrkesskade = GrunnlagYrkesskadeDTO(
                grunnlaget = BigDecimal(25000.0),
                inkludererUføre = false,
                beregningsgrunnlag = BeregningsgrunnlagDTO(
                    grunnlag11_19dto = Grunnlag11_19DTO(
                        inntekter = mapOf("2019" to 25000.0, "2020" to 26000.0),
                        grunnlaget = 20000.0,
                        er6GBegrenset = false,
                        erGjennomsnitt = true,
                    )
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = BigDecimal(30),
                andelYrkesskade = 25,
                benyttetAndelForYrkesskade = 20,
                andelSomIkkeSkyldesYrkesskade = BigDecimal(40),
                antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(25000),
                yrkesskadeTidspunkt = 2018,
                grunnlagForBeregningAvYrkesskadeandel = BigDecimal(25000),
                yrkesskadeinntektIG = BigDecimal(6),
                grunnlagEtterYrkesskadeFordel = BigDecimal(25000),
            ),
        ),
    )
}

fun behandlingHendelse(saksnummer: String, behandlingReferanse: UUID): StoppetBehandling {
    return StoppetBehandling(
        saksnummer = saksnummer,
        behandlingReferanse = behandlingReferanse,
        behandlingStatus = Status.OPPRETTET,
        behandlingType = TypeBehandling.Førstegangsbehandling,
        ident = "14890097570",
        avklaringsbehov = listOf(
            AvklaringsbehovHendelseDto(
                definisjon = DefinisjonDTO(
                    type = AvklaringsbehovKode.`5003`,
                    behovType = BehovType.MANUELT_PÅKREVD,
                    løsesISteg = StegType.AVKLAR_SYKDOM
                ),
                status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                endringer = listOf(
                    EndringDTO(
                        status = EndringStatus.valueOf("OPPRETTET"),
                        tidsstempel = LocalDateTime.parse("2024-08-14T10:35:34.842"),
                        frist = null,
                        endretAv = "Kelvin"
                    ),
                    EndringDTO(
                        status = EndringStatus.valueOf("AVSLUTTET"),
                        tidsstempel = LocalDateTime.parse("2024-08-14T11:50:50.217"),
                        frist = null,
                        endretAv = "Z994573"
                    )
                )
            ),
            AvklaringsbehovHendelseDto(
                definisjon = DefinisjonDTO(
                    type = AvklaringsbehovKode.`5006`,
                    behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                    løsesISteg = StegType.VURDER_BISTANDSBEHOV
                ),
                status = EndringStatus.valueOf("SENDT_TILBAKE_FRA_KVALITETSSIKRER"),
                endringer = listOf(
                    EndringDTO(
                        status = EndringStatus.valueOf("OPPRETTET"),
                        tidsstempel = LocalDateTime.parse("2024-08-14T11:50:52.049"),
                        frist = null,
                        endretAv = "Kelvin"
                    ),
                    EndringDTO(
                        status = EndringStatus.valueOf("AVSLUTTET"),
                        tidsstempel = LocalDateTime.parse("2024-08-14T11:51:16.176"),
                        frist = null,
                        endretAv = "Z994573"
                    )
                )
            ),
            AvklaringsbehovHendelseDto(
                definisjon = DefinisjonDTO(
                    type = AvklaringsbehovKode.`5097`,
                    behovType = BehovType.valueOf("MANUELT_PÅKREVD"),
                    løsesISteg = StegType.KVALITETSSIKRING
                ),
                status = EndringStatus.valueOf("AVSLUTTET"),
                endringer = listOf(
                    EndringDTO(
                        status = EndringStatus.valueOf("OPPRETTET"),
                        tidsstempel = LocalDateTime.parse("2024-08-14T11:51:17.231"),
                        frist = null,
                        endretAv = "Kelvin"
                    ),
                    EndringDTO(
                        status = EndringStatus.valueOf("AVSLUTTET"),
                        tidsstempel = LocalDateTime.parse("2024-08-14T11:54:22.268"),
                        frist = null,
                        endretAv = "Z994573"
                    )
                )
            )
        ),
        behandlingOpprettetTidspunkt = LocalDateTime.parse("2024-08-14T10:35:33.595"),
        versjon = UUID.randomUUID().toString(),
        mottattTid = LocalDateTime.now().minusDays(1),
        sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.UTREDES,
        hendelsesTidspunkt = LocalDateTime.now()
    )
}
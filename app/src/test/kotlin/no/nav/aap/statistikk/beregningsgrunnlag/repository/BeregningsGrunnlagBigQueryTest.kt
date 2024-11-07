package no.nav.aap.statistikk.beregningsgrunnlag.repository

import no.nav.aap.statistikk.avsluttetbehandling.GrunnlagType
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.UføreType
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.schemaRegistry
import no.nav.aap.statistikk.testutils.BigQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class BeregningsGrunnlagBigQueryTest {

    @Test
    fun `lagre beregningsgrunnlag 11-19`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)

        val tabell = BeregningsGrunnlagTabell()

        val grunnlag =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2, 2021 to 146.4)
            )

        client.insert(
            tabell,
            BeregningsGrunnlagBQ(
                saksnummer = "12345",
                behandlingsreferanse = UUID.randomUUID(),
                type = GrunnlagType.Grunnlag11_19,
                grunnlaget = grunnlag.grunnlag,
                standardGrunnlag = grunnlag.grunnlag,
                standardEr6GBegrenset = grunnlag.er6GBegrenset,
                standardErGjennomsnitt = grunnlag.erGjennomsnitt,
            )
        )

        val uthentetResultat = client.read(tabell)

        assertThat(uthentetResultat.size).isEqualTo(1)

        val første = uthentetResultat.first()

//        assertThat(første.inntekter).isEqualTo(mapOf(2020 to 123.2, 2021 to 146.4))
        assertThat(første.standardGrunnlag).isEqualTo(12.2)
        assertThat(første.grunnlaget).isEqualTo(12.2)
        assertThat(første.standardEr6GBegrenset).isEqualTo(true)
        assertThat(første.standardErGjennomsnitt).isEqualTo(false)
    }

    @Test
    fun `lagre uføregrunnlag`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)
        val tabell = BeregningsGrunnlagTabell()

        val grunnlag_11_19 =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2)
            )

        val grunnlag = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 123.456,
            type = UføreType.STANDARD,
            grunnlag11_19 = grunnlag_11_19,
            uføregrad = 80,
            uføreInntekterFraForegåendeÅr = mapOf(2020 to 123.1, 2021 to 145.5),
            uføreYtterligereNedsattArbeidsevneÅr = 1963,
        )

        val behandlingsreferanse = UUID.randomUUID()
        client.insert(
            tabell,
            BeregningsGrunnlagBQ(
                saksnummer = "abcde",
                behandlingsreferanse = behandlingsreferanse,
                type = GrunnlagType.Grunnlag_Ufore,
                grunnlaget = grunnlag.grunnlag,
                standardGrunnlag = grunnlag_11_19.grunnlag,
                standardEr6GBegrenset = grunnlag_11_19.er6GBegrenset,
                standardErGjennomsnitt = grunnlag_11_19.erGjennomsnitt,
                uføreGrunnlag = grunnlag.grunnlag,
                uføreUføregrad = grunnlag.uføregrad,
            )
        )

        val uthentetResultat = client.read(tabell).first()

        assertThat(uthentetResultat).isEqualTo(
            BeregningsGrunnlagBQ(
                saksnummer = "abcde",
                behandlingsreferanse = behandlingsreferanse,
                type = GrunnlagType.Grunnlag_Ufore,
                grunnlaget = grunnlag.grunnlag,
                standardGrunnlag = grunnlag_11_19.grunnlag,
                standardEr6GBegrenset = grunnlag_11_19.er6GBegrenset,
                standardErGjennomsnitt = grunnlag_11_19.erGjennomsnitt,
                uføreGrunnlag = grunnlag.grunnlag,
                uføreUføregrad = grunnlag.uføregrad,
            )
        )
    }

    @Test
    fun `lagre grunnlag yrkesskade`(@BigQuery bigQueryConfig: BigQueryConfig) {
        println(bigQueryConfig)
        val client = BigQueryClient(bigQueryConfig, schemaRegistry)
        val tabell = BeregningsGrunnlagTabell()

        val grunnlag_11_19 =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2)
            )

        val uføre = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 123.456,
            type = UføreType.STANDARD,
            grunnlag11_19 = grunnlag_11_19,
            uføregrad = 80,
            uføreInntekterFraForegåendeÅr = mapOf(2020 to 123.2, 2021 to 145.5),
            uføreYtterligereNedsattArbeidsevneÅr = 1963,
        )

        val grunnlag = IBeregningsGrunnlag.GrunnlagYrkesskade(
            grunnlaget = 1337.123,
            beregningsgrunnlag = uføre,
            terskelverdiForYrkesskade = 50,
            andelSomSkyldesYrkesskade = BigDecimal(20),
            andelYrkesskade = 40,
            benyttetAndelForYrkesskade = 20,
            andelSomIkkeSkyldesYrkesskade = BigDecimal(10),
            antattÅrligInntektYrkesskadeTidspunktet = BigDecimal(50000),
            yrkesskadeTidspunkt = 1992,
            grunnlagForBeregningAvYrkesskadeandel = BigDecimal(1234),
            yrkesskadeinntektIG = BigDecimal(60000),
            grunnlagEtterYrkesskadeFordel = BigDecimal(6123),
        )


        val behandlingsreferanse = UUID.randomUUID()

        client.insert(
            tabell,
            BeregningsGrunnlagBQ(
                saksnummer = "abcde",
                behandlingsreferanse = behandlingsreferanse,
                type = GrunnlagType.Grunnlag_Ufore,
                grunnlaget = grunnlag.grunnlaget,
                standardGrunnlag = grunnlag_11_19.grunnlag,
                standardEr6GBegrenset = grunnlag_11_19.er6GBegrenset,
                standardErGjennomsnitt = grunnlag_11_19.erGjennomsnitt,
                uføreGrunnlag = uføre.grunnlag,
                uføreUføregrad = uføre.uføregrad,
                yrkesskadeTerskelVerdiForYrkesskade = grunnlag.terskelverdiForYrkesskade,
                yrkesskadeAndelSomSkyldesYrkesskade = grunnlag.andelSomSkyldesYrkesskade.toDouble(),
                yrkesskadeAndelSomIkkeSkyldesYrkesskade = grunnlag.andelSomIkkeSkyldesYrkesskade.toDouble(),
                yrkesskadeAndelYrkesskade = grunnlag.andelYrkesskade,
                yrkesskadeBenyttetAndelForYrkesskade = grunnlag.benyttetAndelForYrkesskade,
                yrkesskadeAntattÅrligInntektYrkesskadeTidspunktet = grunnlag.antattÅrligInntektYrkesskadeTidspunktet.toDouble(),
                yrkesskadeYrkesskadeTidspunkt = grunnlag.yrkesskadeTidspunkt,
                yrkesskadeGrunnlagForBeregningAvYrkesskadeandel = grunnlag.grunnlagForBeregningAvYrkesskadeandel.toDouble(),
                yrkesskadeYrkesskadeinntektIG = grunnlag.yrkesskadeinntektIG.toDouble(),
                yrkesskadeGrunnlagEtterYrkesskadeFordel = grunnlag.grunnlagEtterYrkesskadeFordel.toDouble()
            )
        )

        val uthentetResultat = client.read(tabell).first()

        assertThat(uthentetResultat).isEqualTo(
            BeregningsGrunnlagBQ(
                saksnummer = "abcde",
                behandlingsreferanse = behandlingsreferanse,
                type = GrunnlagType.Grunnlag_Ufore,
                grunnlaget = grunnlag.grunnlaget,
                standardGrunnlag = grunnlag_11_19.grunnlag,
                standardEr6GBegrenset = grunnlag_11_19.er6GBegrenset,
                standardErGjennomsnitt = grunnlag_11_19.erGjennomsnitt,
                uføreGrunnlag = uføre.grunnlag,
                uføreUføregrad = uføre.uføregrad,
                yrkesskadeTerskelVerdiForYrkesskade = grunnlag.terskelverdiForYrkesskade,
                yrkesskadeAndelSomSkyldesYrkesskade = grunnlag.andelSomSkyldesYrkesskade.toDouble(),
                yrkesskadeAndelSomIkkeSkyldesYrkesskade = grunnlag.andelSomIkkeSkyldesYrkesskade.toDouble(),
                yrkesskadeAndelYrkesskade = grunnlag.andelYrkesskade,
                yrkesskadeBenyttetAndelForYrkesskade = grunnlag.benyttetAndelForYrkesskade,
                yrkesskadeAntattÅrligInntektYrkesskadeTidspunktet = grunnlag.antattÅrligInntektYrkesskadeTidspunktet.toDouble(),
                yrkesskadeYrkesskadeTidspunkt = grunnlag.yrkesskadeTidspunkt,
                yrkesskadeGrunnlagForBeregningAvYrkesskadeandel = grunnlag.grunnlagForBeregningAvYrkesskadeandel.toDouble(),
                yrkesskadeYrkesskadeinntektIG = grunnlag.yrkesskadeinntektIG.toDouble(),
                yrkesskadeGrunnlagEtterYrkesskadeFordel = grunnlag.grunnlagEtterYrkesskadeFordel.toDouble()
            )
        )
    }
}
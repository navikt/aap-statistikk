package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.testutils.BigQuery
import no.nav.aap.statistikk.api_kontrakt.UføreType
import no.nav.aap.statistikk.api_kontrakt.Vilkårtype
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagTabell
import no.nav.aap.statistikk.beregningsgrunnlag.repository.Beregningsgrunnlag
import no.nav.aap.statistikk.vilkårsresultat.Vilkår
import no.nav.aap.statistikk.vilkårsresultat.VilkårsPeriode
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class BigQueryClientTest {
    @Test
    fun `lage en tabell to ganger er idempotent`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig)

        val vilkårsVurderingTabell = VilkårsVurderingTabell()
        val res = client.create(vilkårsVurderingTabell)
        // Lag tabell før den eksisterer
        assertThat(res).isTrue()

        // Prøv igjen
        val res2: Boolean = client.create(vilkårsVurderingTabell)
        assertThat(res2).isFalse()
    }

    @Test
    fun `sette inn rad og hente ut igjen`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig)

        val vilkårsVurderingTabell = VilkårsVurderingTabell()

        client.create(vilkårsVurderingTabell)

        val behandlingsReferanse = UUID.randomUUID()
        val vilkårsResult = Vilkårsresultat(
            "123", behandlingsReferanse, "behandling", listOf(
                Vilkår(
                    Vilkårtype.MEDLEMSKAP,
                    listOf(
                        VilkårsPeriode(
                            LocalDate.now(),
                            LocalDate.now(),
                            "utfall",
                            false,
                            null,
                            null
                        )
                    )
                )
            )
        )

        client.insert(vilkårsVurderingTabell, vilkårsResult)
        val uthentetResultat = client.read(vilkårsVurderingTabell)

        assertThat(uthentetResultat.size).isEqualTo(1)
        assertThat(uthentetResultat.first().saksnummer).isEqualTo("123")
        assertThat(uthentetResultat.first().behandlingsReferanse).isEqualTo(behandlingsReferanse)
        assertThat(uthentetResultat.first().behandlingsType).isEqualTo("behandling")
        assertThat(uthentetResultat.first().vilkår).hasSize(1)
        assertThat(uthentetResultat.first().vilkår.first().vilkårType).isEqualTo(Vilkårtype.MEDLEMSKAP)
    }

    @Test
    fun `lagre beregningsgrunnlag 11-19`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig)

        val tabell = BeregningsGrunnlagTabell()

        client.create(tabell)

        val grunnlag =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2, 2021 to 146.4)
            )

        client.insert(
            tabell,
            Beregningsgrunnlag(value = grunnlag, behandlingsReferanse = UUID.randomUUID())
        )

        val uthentetResultat = client.read(tabell)

        assertThat(uthentetResultat.size).isEqualTo(1)

        val første = uthentetResultat.first() as MedBehandlingsreferanse<*>

        første.value as IBeregningsGrunnlag.Grunnlag_11_19

        assertThat(første.value.inntekter).isEqualTo(mapOf(2020 to 123.2, 2021 to 146.4))
        assertThat(første.value.grunnlag).isEqualTo(12.2)
        assertThat(første.value.er6GBegrenset).isEqualTo(true)
        assertThat(første.value.erGjennomsnitt).isEqualTo(false)
    }

    @Test
    fun `lagre uføregrunnlag`(@BigQuery bigQueryConfig: BigQueryConfig) {
        val client = BigQueryClient(bigQueryConfig)
        val tabell = BeregningsGrunnlagTabell()

        client.create(tabell)

        val grunnlag_11_19 =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2)
            )

        val grunnlag = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 123.456,
            er6GBegrenset = false,
            type = UføreType.STANDARD,
            grunnlag11_19 = grunnlag_11_19,
            uføregrad = 80,
            uføreInntekterFraForegåendeÅr = mapOf(2020 to 123.1, 2021 to 145.5),
            uføreInntektIKroner = BigDecimal(123456789.00),
            uføreYtterligereNedsattArbeidsevneÅr = 1963,
        )

        val behandlingsReferanse = UUID.randomUUID()
        client.insert(
            tabell,
            Beregningsgrunnlag(value = grunnlag, behandlingsReferanse = behandlingsReferanse)
        )

        val uthentetResultat = client.read(tabell).first() as MedBehandlingsreferanse<*>

        assertThat(uthentetResultat).isEqualTo(
            Beregningsgrunnlag(
                value = grunnlag,
                behandlingsReferanse = behandlingsReferanse
            )
        )
    }

    @Test
    fun `lagre grunnlag yrkesskade`(@BigQuery bigQueryConfig: BigQueryConfig) {
        println(bigQueryConfig)
        val client = BigQueryClient(bigQueryConfig)
        val tabell = BeregningsGrunnlagTabell()

        val created = client.create(tabell)
        assertThat(created).isTrue()

        val grunnlag_11_19 =
            IBeregningsGrunnlag.Grunnlag_11_19(
                12.2,
                er6GBegrenset = true,
                erGjennomsnitt = false,
                inntekter = mapOf(2020 to 123.2)
            )

        val uføre = IBeregningsGrunnlag.GrunnlagUføre(
            grunnlag = 123.456,
            er6GBegrenset = false,
            type = UføreType.STANDARD,
            grunnlag11_19 = grunnlag_11_19,
            uføregrad = 80,
            uføreInntekterFraForegåendeÅr = mapOf(2020 to 123.2, 2021 to 145.5),
            uføreInntektIKroner = BigDecimal(123456789.00),
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
            er6GBegrenset = true
        )


        val behandlingsReferanse = UUID.randomUUID()
        client.insert(
            tabell,
            Beregningsgrunnlag(value = grunnlag, behandlingsReferanse = behandlingsReferanse)
        )

        val uthentetResultat = client.read(tabell).first() as MedBehandlingsreferanse<*>

        assertThat(uthentetResultat).isEqualTo(
            MedBehandlingsreferanse(
                value = grunnlag,
                behandlingsReferanse = behandlingsReferanse
            )
        )
    }
}
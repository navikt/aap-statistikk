package no.nav.aap.statistikk.avsluttetbehandling.api

import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.hendelser.api.Tags
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingDTOJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

val eksempelUUID = UUID.randomUUID()

private val logger = LoggerFactory.getLogger("avsluttetBehandlingRoute")

fun NormalOpenAPIRoute.avsluttetBehandling(
    jobbAppender: JobbAppender,
    lagreAvsluttetBehandlingDTOJobb: LagreAvsluttetBehandlingDTOJobb,
) {
    val exampleRequest = AvsluttetBehandlingDTO(
        saksnummer = "4LELS7K",
        behandlingsReferanse = eksempelUUID,
        tilkjentYtelse = TilkjentYtelseDTO(
            perioder = listOf(
                TilkjentYtelsePeriodeDTO(
                    fraDato = LocalDate.now(),
                    tilDato = LocalDate.now().plusYears(1),
                    dagsats = 1000.0,
                    gradering = 0.0
                )
            )
        ),
        vilkårsResultat = VilkårsResultatDTO(
            typeBehandling = "Førstegangsbehandling",
            vilkår = listOf(
                VilkårDTO(
                    Vilkårtype.GRUNNLAGET, perioder = listOf(
                        VilkårsPeriodeDTO(
                            fraDato = LocalDate.now().minusWeeks(2),
                            tilDato = LocalDate.now(),
                            utfall = Utfall.OPPFYLT,
                            manuellVurdering = true
                        )
                    )
                )
            )
        ),
        beregningsGrunnlag = BeregningsgrunnlagDTO(
            grunnlagYrkesskade = GrunnlagYrkesskadeDTO(
                grunnlaget = BigDecimal.valueOf(100000),
                beregningsgrunnlag = BeregningsgrunnlagDTO(
                    grunnlag11_19dto = Grunnlag11_19DTO(
                        inntekter = mapOf(
                            "2021" to 100000.0,
                            "2022" to 10000.0,
                            "2023" to 1000.0,
                        ),
                        grunnlaget = 5.5,
                        er6GBegrenset = false,
                        erGjennomsnitt = false,
                    )
                ),
                terskelverdiForYrkesskade = 70,
                andelSomSkyldesYrkesskade = BigDecimal.valueOf(60),
                andelYrkesskade = 60,
                benyttetAndelForYrkesskade = 60,
                andelSomIkkeSkyldesYrkesskade = BigDecimal.valueOf(40),
                inkludererUføre = false,
                antattÅrligInntektYrkesskadeTidspunktet = BigDecimal.valueOf(500000),
                yrkesskadeTidspunkt = 1999,
                grunnlagForBeregningAvYrkesskadeandel = BigDecimal.valueOf(10000),
                yrkesskadeinntektIG = BigDecimal.valueOf(100000),
                grunnlagEtterYrkesskadeFordel = BigDecimal.valueOf(100000),
            )
        )
    )

    route("/avsluttetBehandling") {
        post<Unit, String, AvsluttetBehandlingDTO>(
            TagModule(listOf(Tags.AvsluttetBehandling)), exampleRequest = exampleRequest
        ) { _, dto ->
            logger.info("Mottok avsluttet behandling: $dto")

            jobbAppender.leggTil(
                JobbInput(lagreAvsluttetBehandlingDTOJobb).medPayload(
                    DefaultJsonMapper.toJson(dto)
                )
            )

            // TODO: responder med id?
            responder.respond(
                HttpStatusCode.Accepted,
                "{}",
                pipeline
            )
        }
    }
}
package no.nav.aap.statistikk.avsluttetbehandling.api

import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.ktor.server.application.*
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelser.api.Tags
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

data class AvsluttetBehandlingResponsDTO(val id: Int)

val eksempelUUID = UUID.randomUUID()

fun NormalOpenAPIRoute.avsluttetBehandling(avsluttetBehandlingService: AvsluttetBehandlingService) {
    route("/avsluttetBehandling") {
        post<Unit, AvsluttetBehandlingResponsDTO, AvsluttetBehandlingDTO>(
            TagModule(listOf(Tags.AvsluttetBehandling)), exampleRequest = AvsluttetBehandlingDTO(
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
        ) { _, dto ->
            pipeline.context.application.log.info("Mottok avsluttet behandling: $dto")

            val id = avsluttetBehandlingService.lagre(dto.tilDomene())

            // TODO: responder med id?
            responder.respond(
                HttpStatusCode.Accepted,
                AvsluttetBehandlingResponsDTO(id = 143),
                pipeline
            )
        }
    }
}
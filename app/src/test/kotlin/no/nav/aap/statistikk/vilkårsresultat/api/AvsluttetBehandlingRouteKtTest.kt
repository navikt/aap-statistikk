package no.nav.aap.statistikk.vilkårsresultat.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import no.nav.aap.statistikk.api.testKlient
import no.nav.aap.statistikk.avsluttetbehandling.api.TilkjentYtelsePeriodeDTO
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.vilkårsresultat.service.VilkårsResultatService
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class AvsluttetBehandlingRouteKtTest {
    @Test
    fun `kan parse avsluttet behandling dto og returnerer database-id`() {
        val hendelsesRepository = mockk<IHendelsesRepository>()
        val vilkårsResultatService = mockk<VilkårsResultatService>()
        val avsluttetBehandlingService = mockk<AvsluttetBehandlingService>()

        every { avsluttetBehandlingService.lagre(any()) } just Runs
        every { vilkårsResultatService.mottaVilkårsResultat(any()) } returns 1

        testKlient(hendelsesRepository, vilkårsResultatService, avsluttetBehandlingService) { client ->
            val response = client.post("/avsluttetBehandling") {
                val vilkårsresultat =
                    VilkårsResultatDTO(
                        typeBehandling = "Førstegangsbehandling", saksnummer = "ABC", vilkår = listOf(
                            VilkårDTO(
                                vilkårType = "ALDERS",
                                listOf(
                                    VilkårsPeriodeDTO(
                                        fraDato = LocalDate.now().minusDays(10),
                                        tilDato = LocalDate.now().minusDays(0),
                                        utfall = Utfall.IKKE_OPPFYLT,
                                        manuellVurdering = true
                                    )
                                )
                            ),
                        )
                    )

                // konverter til json med jackson
                val vilkårsResultatJson = ObjectMapper()
                    .registerModule(JavaTimeModule())
                    .writeValueAsString(vilkårsresultat)

                println(vilkårsResultatJson)

                val tilkjentYtelseDTO =
                    listOf(
                        TilkjentYtelsePeriodeDTO(
                            fraDato = LocalDate.now().minusDays(10),
                            tilDato = LocalDate.now(),
                            dagsats = BigDecimal("100.23"),
                            gradering = BigDecimal("70.2")

                        )
                    )

                val tilkjentYtelseJSON =
                    ObjectMapper().registerModule(JavaTimeModule()).writeValueAsString(tilkjentYtelseDTO)

                contentType(ContentType.Application.Json)
                val behandlingReferanse = UUID.randomUUID()

                @Language("JSON") val jsonBody =
                    """{
  "sakId": "4LENXDC",
  "behandlingsReferanse": "$behandlingReferanse",
  "tilkjentYtelse": $tilkjentYtelseJSON,
  "vilkårsResultat": $vilkårsResultatJson
}"""
                setBody(jsonBody)
            }

            assertThat(response.status.isSuccess()).isTrue()
            assertThat(response.body<VilkårsResultatResponsDTO>().id).isEqualTo(122)
        }
    }
}
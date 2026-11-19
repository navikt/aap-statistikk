package no.nav.aap.statistikk.testutils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class UpdateHendelserPublicJobbJsonTest {
    private val mapper = jacksonObjectMapper()
    private val directory = Files.createTempDirectory(Path.of(".."), "temp_fixtures_")
    private val fixture = directory.resolve("hendelser_public_jobb.json")

    @AfterEach
    fun ryddTestfiler() {
        Files.deleteIfExists(fixture)
        Files.delete(directory)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `oppdaterer direkte payloads uten å overskrive eksisterende verdier`(somArray: Boolean) {
        val payload = """
            {
              "årsakTilOpprettelse": "REVURDERING",
              "avsluttetBehandling": {
                "tilkjentYtelse": {
                  "perioder": [
                    {"tilDato": "2026-11-18"},
                    {"tilDato": "2026-11-19", "utbetalingsdato": "2026-11-23", "minsteSats": "EKSISTERENDE"}
                  ]
                }
              }
            }
        """.trimIndent()
        Files.writeString(fixture, if (somArray) "[$payload]" else payload)

        UpdateHendelserPublicJobbJson().oppdater(listOf(fixture))

        val root = mapper.readTree(Files.readString(fixture))
        val oppdatert = if (somArray) root[0] else root
        assertThat(oppdatert["årsakTilOpprettelse"].asText()).isEqualTo("REVURDERING")
        val avsluttet = oppdatert["avsluttetBehandling"]
        assertThat(avsluttet["perioderMedArbeidsopptrapping"].isEmpty).isTrue()
        assertThat(avsluttet["institusjonsopphold"].isEmpty).isTrue()
        assertThat(avsluttet["samordning"]["uføre"].isEmpty).isTrue()
        val perioder = avsluttet["tilkjentYtelse"]["perioder"]
        assertThat(perioder[0]["utbetalingsdato"].asText()).isEqualTo("2026-11-19")
        assertThat(perioder[0]["minsteSats"].asText()).isEqualTo("IKKE_MINSTESATS")
        assertThat(perioder[1]["utbetalingsdato"].asText()).isEqualTo("2026-11-23")
        assertThat(perioder[1]["minsteSats"].asText()).isEqualTo("EKSISTERENDE")

        val etterFørsteKjøring = Files.readString(fixture)
        UpdateHendelserPublicJobbJson().oppdater(listOf(fixture))
        assertThat(Files.readString(fixture)).isEqualTo(etterFørsteKjøring)
    }

    @Test
    fun `oppdaterer Motor-payloads og tidspunkt i hendelsesfilen`() {
        val oppgave = mapper.writeValueAsString(mapOf("sendtTid" to "2026-11-18 10:00:00.000"))
        val behandling = mapper.writeValueAsString(mapOf("hendelsesTidspunkt" to "2026-11-18T11:00:00"))
        Files.writeString(
            fixture,
            mapper.writeValueAsString(
                listOf(
                    mapOf(
                        "type" to "statistikk.lagreOppgaveHendelseJobb",
                        "opprettet_tid" to "2026-11-18 10:00:00.000",
                        "payload" to oppgave,
                    ),
                    mapOf("type" to "statistikk.lagreHendelse", "payload" to behandling),
                ),
            ),
        )

        UpdateHendelserPublicJobbJson().oppdater(listOf(fixture))

        val rader = mapper.readTree(Files.readString(fixture))
        val oppdatertOppgave = mapper.readTree(rader[0]["payload"].asText())
        val oppdatertBehandling = mapper.readTree(rader[1]["payload"].asText())
        assertThat(oppdatertOppgave["sendtTid"].asText()).isEqualTo("2026-11-18T10:00")
        assertThat(oppdatertBehandling["årsakTilOpprettelse"].asText()).isEqualTo("SØKNAD")
        assertThat(oppdatertBehandling["vedtakstidspunkt"].asText()).isEqualTo("2026-11-18T11:00:00")
    }

    @Test
    fun `manglende fixture gir feil i stedet for å bli hoppet over`() {
        assertThatThrownBy { UpdateHendelserPublicJobbJson().oppdater(listOf(fixture)) }
            .isInstanceOf(NoSuchFileException::class.java)
    }
}

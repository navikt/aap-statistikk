package no.nav.aap.statistikk.testutils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Test-only helper that updates test fixture files to ensure they have all required fields.
 * 
 * Run this test to update test data files when the data model changes:
 * ./gradlew test --tests "no.nav.aap.statistikk.testutils.UpdateHendelserPublicJobbJson.update_hendelser_public_jobb_fixture_with_utbetalingsdato"
 * 
 * The script automatically:
 * - Adds missing required fields with default values
 * - Fixes date/time format issues
 * - Adds calculated fields (e.g., utbetalingsdato from tilDato)
 */
class UpdateHendelserPublicJobbJson {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val opprettetTidFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    // Pipeline av transformasjoner som skal kjøres på behandling-noder
    private val behandlingTransforms: List<NodeTransform> = listOf(
        ÅrsakTilOpprettelseTransform(),
        PerioderMedArbeidsopptrappingTransform(),
        TilkjentYtelseTransform()
    )

    @Test
    fun update_hendelser_public_jobb_fixture_with_utbetalingsdato() {
        val testDataFiles = listOf(
            "app/src/test/resources/hendelser_public_jobb.json",
            "src/test/resources/hendelser_public_jobb.json",
            "app/src/test/resources/hendelser_klage.json",
            "src/test/resources/hendelser_klage.json",
            "app/src/test/resources/avklaringsbehovhendelser/fullfort_forstegangsbehandling.json",
            "app/src/test/resources/avklaringsbehovhendelser/grunnlag_steg.json",
            "app/src/test/resources/avklaringsbehovhendelser/er_pa_brev_steget.json",
            "app/src/test/resources/avklaringsbehovhendelser/meldekort_behandling.json",
            "app/src/test/resources/avklaringsbehovhendelser/sendt_tilbake_11_5_fra_beslutter.json",
            "app/src/test/resources/avklaringsbehovhendelser/skal_være_iverksettes.json",
            "app/src/test/resources/avklaringsbehovhendelser/avbrutt_revurdering.json",
            "app/src/test/resources/avklaringsbehovhendelser/resendt_hendelse.json",
            "app/src/test/resources/avklaringsbehovhendelser/resendt_revurdering_automatisk.json"
        ).map { Path.of(it) }

        testDataFiles.filter { Files.exists(it) }.forEach { filePath ->
            val jsonNode = mapper.readTree(Files.readString(filePath))
            var modified = false

            when (jsonNode) {
                is ArrayNode -> modified = processArrayNode(jsonNode, filePath)
                is ObjectNode -> modified = processObjectNode(jsonNode)
            }

            if (modified) {
                Files.writeString(filePath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode))
            }
        }
    }

    private fun processArrayNode(jsonNode: ArrayNode, filePath: Path): Boolean {
        var modified = false
        
        for (elem in jsonNode) {
            if (elem !is ObjectNode) continue

            val payloadText = elem.get("payload")?.asText()
            if (payloadText != null) {
                // Wrapped format: {type: "...", payload: "escaped_json", opprettet_tid: "..."}
                modified = processWrappedFormat(elem, payloadText) || modified
            } else {
                // Raw format: direct StoppetBehandling object
                modified = applyCommonBehandlingUpdates(elem) || modified
            }
        }

        // Special handling for hendelser_public_jobb.json
        if (filePath.fileName.toString() == "hendelser_public_jobb.json") {
            modified = addVedtakstidspunktToLastLagreHendelse(jsonNode) || modified
        }

        return modified
    }

    private fun processWrappedFormat(elem: ObjectNode, payloadText: String): Boolean {
        val payloadNode = try {
            mapper.readTree(payloadText) as? ObjectNode
        } catch (_: Exception) {
            null
        } ?: return false

        var innerModified = false

        // Update oppgave hendelser
        if (elem.get("type")?.asText() == "statistikk.lagreOppgaveHendelseJobb") {
            innerModified = updateOppgaveHendelse(payloadNode, elem) || innerModified
        }

        // Update behandling hendelser
        innerModified = applyCommonBehandlingUpdates(payloadNode) || innerModified

        if (innerModified) {
            elem.put("payload", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloadNode))
            return true
        }

        return false
    }

    private fun updateOppgaveHendelse(payloadNode: ObjectNode, wrapperElem: ObjectNode): Boolean {
        val existingSendtTid = payloadNode.get("sendtTid")?.asText()
        val opprettetTidText = wrapperElem.get("opprettet_tid")?.asText()

        if (opprettetTidText != null && (existingSendtTid == null || existingSendtTid.contains(" "))) {
            try {
                val sendtTidIso = LocalDateTime.parse(opprettetTidText, opprettetTidFormatter).toString()
                payloadNode.put("sendtTid", sendtTidIso)
                return true
            } catch (_: Exception) {
                // Skip if parsing fails
            }
        }

        return false
    }

    private fun applyCommonBehandlingUpdates(node: ObjectNode): Boolean {
        return behandlingTransforms.fold(false) { modified, transform ->
            transform.apply(node, mapper) || modified
        }
    }

    // Interface for transformasjoner - gjør det enkelt å legge til nye
    private interface NodeTransform {
        fun apply(node: ObjectNode, mapper: ObjectMapper): Boolean
    }

    // Legger til årsakTilOpprettelse hvis den mangler
    private class ÅrsakTilOpprettelseTransform : NodeTransform {
        override fun apply(node: ObjectNode, mapper: ObjectMapper): Boolean {
            if (!node.has("årsakTilOpprettelse")) {
                node.put("årsakTilOpprettelse", "SØKNAD")
                return true
            }
            return false
        }
    }

    // Legger til tom perioderMedArbeidsopptrapping hvis den mangler
    private class PerioderMedArbeidsopptrappingTransform : NodeTransform {
        override fun apply(node: ObjectNode, mapper: ObjectMapper): Boolean {
            val avsluttetBehandlingNode = node.path("avsluttetBehandling")
            if (avsluttetBehandlingNode is ObjectNode && !avsluttetBehandlingNode.has("perioderMedArbeidsopptrapping")) {
                avsluttetBehandlingNode.set<ArrayNode>("perioderMedArbeidsopptrapping", mapper.createArrayNode())
                return true
            }
            return false
        }
    }

    // Legger til manglende felter i tilkjentYtelse perioder
    private class TilkjentYtelseTransform : NodeTransform {
        override fun apply(node: ObjectNode, mapper: ObjectMapper): Boolean {
            val tilkjentYtelsePerioder = node
                .path("avsluttetBehandling")
                .path("tilkjentYtelse")
                .path("perioder")

            if (tilkjentYtelsePerioder !is ArrayNode || tilkjentYtelsePerioder.isEmpty) {
                return false
            }

            var modified = false
            for (periodeNode in tilkjentYtelsePerioder) {
                if (periodeNode !is ObjectNode) continue

                // Legg til utbetalingsdato hvis den mangler
                val tilDatoText = periodeNode.get("tilDato")?.asText()
                if (!tilDatoText.isNullOrBlank() && !periodeNode.has("utbetalingsdato")) {
                    try {
                        val utbetalingsdato = LocalDate.parse(tilDatoText).plusDays(1).toString()
                        periodeNode.put("utbetalingsdato", utbetalingsdato)
                        modified = true
                    } catch (_: Exception) {
                        // Skip if date parsing fails
                    }
                }

                // Legg til minsteSats hvis den mangler
                if (!periodeNode.has("minsteSats")) {
                    periodeNode.put("minsteSats", "IKKE_MINSTESATS")
                    modified = true
                }
            }

            return modified
        }
    }

    private fun addVedtakstidspunktToLastLagreHendelse(jsonNode: ArrayNode): Boolean {
        val lastLagreHendelseIndex = jsonNode.indexOfLast { elem ->
            elem is ObjectNode && elem.get("type")?.asText() == "statistikk.lagreHendelse"
        }

        if (lastLagreHendelseIndex < 0) return false

        val lastElem = jsonNode.get(lastLagreHendelseIndex) as? ObjectNode ?: return false
        val payloadText = lastElem.get("payload")?.asText() ?: return false
        
        val payloadNode = try {
            mapper.readTree(payloadText) as? ObjectNode
        } catch (_: Exception) {
            null
        } ?: return false

        if (payloadNode.has("vedtakstidspunkt")) return false

        val hendelsesTidspunkt = payloadNode.get("hendelsesTidspunkt")?.asText()
        if (hendelsesTidspunkt.isNullOrBlank()) return false

        payloadNode.put("vedtakstidspunkt", hendelsesTidspunkt)
        lastElem.put("payload", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloadNode))
        
        return true
    }

    private fun processObjectNode(jsonNode: ObjectNode): Boolean {
        // Disse filene er StoppetBehandling-objekter, ikke wrappede payloads
        return applyCommonBehandlingUpdates(jsonNode)
    }
}
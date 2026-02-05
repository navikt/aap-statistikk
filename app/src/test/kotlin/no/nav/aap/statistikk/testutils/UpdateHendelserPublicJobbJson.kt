package no.nav.aap.statistikk.testutils

import com.fasterxml.jackson.databind.JsonNode
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
 * Test-only helper that updates the test fixture hendelser_public_jobb.json by
 * inserting "utbetalingsdato" for every period under
 * avsluttetBehandling.tilkjentYtelse.perioder. The value is set to one day
 * after the period's "tilDato".
 *
 * Run this test once to rewrite the file, then commit the changes.
 */
class UpdateHendelserPublicJobbJson {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val opprettetTidFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Test
    fun update_hendelser_public_jobb_fixture_with_utbetalingsdato() {
        val candidates = listOf(
            Path.of("app/src/test/resources/hendelser_public_jobb.json"),
            Path.of("src/test/resources/hendelser_public_jobb.json"),
            Path.of("app/src/test/resources/hendelser_klage.json"),
            Path.of("src/test/resources/hendelser_klage.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/fullfort_forstegangsbehandling.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/grunnlag_steg.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/er_pa_brev_steget.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/meldekort_behandling.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/sendt_tilbake_11_5_fra_beslutter.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/skal_være_iverksettes.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/avbrutt_revurdering.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/resendt_hendelse.json"),
            Path.of("app/src/test/resources/avklaringsbehovhendelser/resendt_revurdering_automatisk.json")
        )

        candidates.filter { Files.exists(it) }.forEach { rootPath ->
            val original = Files.readString(rootPath)
            val jsonNode = mapper.readTree(original)

            var modified = false

            if (jsonNode is ArrayNode) {
                // Logic for hendelser_public_jobb.json (Array of {type, payload}) 
                // OR hendelser_klage.json if it was an array of objects but here it seems to be handled differently?
                // Wait, hendelser_klage.json is List<StoppetBehandling> according to IntegrationTest.
                // If it is List<StoppetBehandling>, then jsonNode is ArrayNode and elements are StoppetBehandling objects.

                // Let's re-evaluate. 
                // hendelser_public_jobb.json: Array of {type: "...", payload: "escaped_json"}
                // hendelser_klage.json: Array of StoppetBehandling (raw JSON objects)

                for (elem in jsonNode) {
                    if (elem !is ObjectNode) continue

                    // Check if it's the wrapped format (public_jobb)
                    val payloadText = elem.get("payload")?.asText()
                    if (payloadText != null) {
                        // Wrapped format
                        val payloadNode = try {
                            mapper.readTree(payloadText)
                        } catch (_: Exception) {
                            null
                        }

                        if (payloadNode is ObjectNode) {
                            var innerModified = false
                            
                            // Add sendtTid for oppgave hendelser
                            if (elem.get("type")?.asText() == "statistikk.lagreOppgaveHendelseJobb") {
                                val existingSendtTid = payloadNode.get("sendtTid")?.asText()
                                val opprettetTidText = elem.get("opprettet_tid")?.asText()
                                
                                // Update if missing or in wrong format
                                if (opprettetTidText != null && 
                                    (existingSendtTid == null || existingSendtTid.contains(" "))) {
                                    try {
                                        val opprettetTid = LocalDateTime.parse(opprettetTidText, opprettetTidFormatter)
                                        payloadNode.put("sendtTid", opprettetTid.toString())
                                        innerModified = true
                                    } catch (_: Exception) {
                                        // Skip if parsing fails
                                    }
                                }
                            }
                            
                            if (!payloadNode.has("årsakTilOpprettelse")) {
                                payloadNode.put("årsakTilOpprettelse", "SØKNAD")
                                innerModified = true
                            }

                            // Existing utbetalingsdato logic
                            val tilkjentYtelsePerioder = payloadNode
                                .path("avsluttetBehandling")
                                .path("tilkjentYtelse")
                                .path("perioder")

                            if (tilkjentYtelsePerioder is ArrayNode && tilkjentYtelsePerioder.size() > 0) {
                                for (periodeNode in tilkjentYtelsePerioder) {
                                    if (periodeNode is ObjectNode) {
                                        val tilDatoText = periodeNode.get("tilDato")?.asText()
                                        val hasUtbetalingsdato = periodeNode.has("utbetalingsdato")
                                        if (!tilDatoText.isNullOrBlank() && !hasUtbetalingsdato) {
                                            val utbetalingsdato = try {
                                                LocalDate.parse(tilDatoText).plusDays(1).toString()
                                            } catch (_: Exception) {
                                                null
                                            }
                                            if (utbetalingsdato != null) {
                                                periodeNode.put("utbetalingsdato", utbetalingsdato)
                                                innerModified = true
                                            }
                                        }
                                    }
                                }
                            }

                            // Add perioderMedArbeidsopptrapping if missing
                            val avsluttetBehandlingNode = payloadNode.path("avsluttetBehandling")
                            if (avsluttetBehandlingNode is ObjectNode && !avsluttetBehandlingNode.has("perioderMedArbeidsopptrapping")) {
                                avsluttetBehandlingNode.set<ArrayNode>("perioderMedArbeidsopptrapping", mapper.createArrayNode())
                                innerModified = true
                            }

                            if (innerModified) {
                                val updatedPayloadText =
                                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloadNode)
                                elem.put("payload", updatedPayloadText)
                                modified = true
                            }
                        }
                    } else {
                        // Raw StoppetBehandling format (klage)
                        if (!elem.has("årsakTilOpprettelse")) {
                            elem.put("årsakTilOpprettelse", "SØKNAD")
                            modified = true
                        }
                        
                        // Add perioderMedArbeidsopptrapping if missing in raw format
                        val avsluttetBehandlingNode = elem.path("avsluttetBehandling")
                        if (avsluttetBehandlingNode is ObjectNode && !avsluttetBehandlingNode.has("perioderMedArbeidsopptrapping")) {
                            avsluttetBehandlingNode.set<ArrayNode>("perioderMedArbeidsopptrapping", mapper.createArrayNode())
                            modified = true
                        }
                    }
                }

                // Add vedtakstidspunkt to the last statistikk.lagreHendelse payload if missing (for public_jobb)
                if (rootPath.fileName.toString() == "hendelser_public_jobb.json") {
                    var lastLagreHendelseIndex = -1
                    for ((idx, elem) in jsonNode.withIndex()) {
                        if (elem is ObjectNode && elem.get("type")?.asText() == "statistikk.lagreHendelse") {
                            lastLagreHendelseIndex = idx
                        }
                    }
                    if (lastLagreHendelseIndex >= 0) {
                        val lastElem = jsonNode.get(lastLagreHendelseIndex) as? ObjectNode
                        val pText = lastElem?.get("payload")?.asText()
                        if (!pText.isNullOrBlank()) {
                            val pNode = try { mapper.readTree(pText) } catch (_: Exception) { null }
                            if (pNode is ObjectNode && !pNode.has("vedtakstidspunkt")) {
                                val candidate = pNode.get("hendelsesTidspunkt")?.asText()
                                if (!candidate.isNullOrBlank()) {
                                    pNode.put("vedtakstidspunkt", candidate)
                                    val updatedPText = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pNode)
                                    lastElem.put("payload", updatedPText)
                                    modified = true
                                }
                            }
                        }
                    }
                }
            } else if (jsonNode is ObjectNode) {
                // Single StoppetBehandling object (avklaringsbehovhendelser files)
                if (!jsonNode.has("årsakTilOpprettelse")) {
                    jsonNode.put("årsakTilOpprettelse", "SØKNAD")
                    modified = true
                }
                
                // Add perioderMedArbeidsopptrapping if missing
                val avsluttetBehandlingNode = jsonNode.path("avsluttetBehandling")
                if (avsluttetBehandlingNode is ObjectNode && !avsluttetBehandlingNode.has("perioderMedArbeidsopptrapping")) {
                    avsluttetBehandlingNode.set<ArrayNode>("perioderMedArbeidsopptrapping", mapper.createArrayNode())
                    modified = true
                }
            }

            if (modified) {
                val updated = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode)
                Files.writeString(rootPath, updated)
            }
        }
    }
}

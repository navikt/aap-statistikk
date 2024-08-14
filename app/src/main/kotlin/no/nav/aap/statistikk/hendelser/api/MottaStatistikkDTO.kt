package no.nav.aap.statistikk.hendelser.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.papsign.ktor.openapigen.annotations.Request
import com.papsign.ktor.openapigen.annotations.type.`object`.example.ExampleProvider
import com.papsign.ktor.openapigen.annotations.type.`object`.example.WithExample
import java.time.LocalDateTime
import java.util.*

@Request("Denne sendes ved hvert stopp i en behandling. Brukes for å populere basen med ID-er.")
@WithExample
data class MottaStatistikkDTO(
    @JsonProperty(value = "saksnummer", required = true) val saksnummer: String,
    val behandlingReferanse: UUID,
    val behandlingOpprettetTidspunkt: LocalDateTime,
    @JsonProperty(value = "status", required = true) val status: String,
    @JsonProperty(value = "behandlingType", required = true) val behandlingType: String,
    val ident: String,
    val avklaringsbehov: List<Any> = listOf(),
) {
    companion object : ExampleProvider<MottaStatistikkDTO> {
        override val example: MottaStatistikkDTO = MottaStatistikkDTO(
            saksnummer = "123456789",
            behandlingReferanse = UUID.randomUUID(),
            status = "OPPRETTET",
            behandlingType = "Førstegangsbehandling",
            ident = "1403199012345",
            behandlingOpprettetTidspunkt = LocalDateTime.now()
        )
    }
}

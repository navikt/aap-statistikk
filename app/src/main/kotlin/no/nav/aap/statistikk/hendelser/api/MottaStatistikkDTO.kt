package no.nav.aap.statistikk.hendelser.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.papsign.ktor.openapigen.annotations.Request
import com.papsign.ktor.openapigen.annotations.type.`object`.example.ExampleProvider
import com.papsign.ktor.openapigen.annotations.type.`object`.example.WithExample
import no.nav.aap.statistikk.api_kontrakt.*
import no.nav.aap.statistikk.avsluttetbehandling.api.eksempelUUID
import java.time.LocalDateTime
import java.util.*


@Request("Denne sendes ved hvert stopp i en behandling. Brukes for å populere basen med ID-er.")
@WithExample
data class MottaStatistikkDTO(
    @JsonProperty(value = "saksnummer", required = true) val saksnummer: String,
    val behandlingReferanse: UUID,
    val behandlingOpprettetTidspunkt: LocalDateTime,
    @JsonProperty(value = "status", required = true) val status: String,
    @JsonProperty(value = "behandlingType", required = true) val behandlingType: TypeBehandling,
    val ident: String,
    val avklaringsbehov: List<AvklaringsbehovHendelse>
) {
    companion object : ExampleProvider<MottaStatistikkDTO> {
        override val example: MottaStatistikkDTO = MottaStatistikkDTO(
            saksnummer = "4LFL5CW",
            behandlingReferanse = eksempelUUID,
            status = "OPPRETTET",
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "1403199012345",
            behandlingOpprettetTidspunkt = LocalDateTime.now(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = 5001,
                        behovType = BehovType.MANUELT_PÅKREVD,
                        løsesISteg = "AVKLAR_STUDENT"

                    ),
                    status = EndringStatus.AVSLUTTET,
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.OPPRETTET,
                            tidsstempel = LocalDateTime.now().minusMinutes(10),
                            endretAv = "Kelvin"
                        ),
                        Endring(
                            status = EndringStatus.AVSLUTTET,
                            tidsstempel = LocalDateTime.now().minusMinutes(5),
                            endretAv = "Z994573"
                        )
                    )
                ),
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = 5003,
                        behovType = BehovType.MANUELT_PÅKREVD,
                        løsesISteg = "AVKLAR_SYKDOM"
                    ),
                    status = EndringStatus.OPPRETTET,
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.OPPRETTET,
                            tidsstempel = LocalDateTime.now().minusMinutes(3),
                            endretAv = "Kelvin"
                        )
                    )
                )
            )
        )
    }
}
package no.nav.aap.statistikk.api

import com.fasterxml.jackson.annotation.JsonProperty

data class MottaStatistikkDTO(
    @JsonProperty(value = "saksnummer", required = true) val saksNummer: String,
    @JsonProperty(value = "status", required = true) val status: String,
    @JsonProperty(value="behandlingsType", required = true) val behandlingsType: String,
)

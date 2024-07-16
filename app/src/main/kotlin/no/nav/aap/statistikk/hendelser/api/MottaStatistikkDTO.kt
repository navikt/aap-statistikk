package no.nav.aap.statistikk.hendelser.api

import com.fasterxml.jackson.annotation.JsonProperty

data class MottaStatistikkDTO(
    @JsonProperty(value = "saksnummer", required = true) val saksNummer: String,
    @JsonProperty(value = "status", required = true) val status: String,
    @JsonProperty(value="behandlingType", required = true) val behandlingsType: String,
)
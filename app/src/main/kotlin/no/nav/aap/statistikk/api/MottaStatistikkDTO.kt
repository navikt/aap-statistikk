package no.nav.aap.statistikk.api

import com.fasterxml.jackson.annotation.JsonProperty

data class MottaStatistikkDTO(@JsonProperty(value = "sakId", required = true) val sakId: String) {

}

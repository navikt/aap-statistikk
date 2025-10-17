package no.nav.aap.statistikk.api

import com.papsign.ktor.openapigen.APITag

enum class Tags(override val description: String) : APITag {
    Produksjonsstyring(
        "Endepunkter relatert til produksjonsstyring."
    ),
    MottaStatistikk(
        "Dette endepunktet brukes for å motta statistikk ved stopp i behandlingen."
    ),
}

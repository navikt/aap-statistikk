package no.nav.aap.statistikk.integrasjoner.pdl

import no.nav.aap.komponenter.gateway.Gateway


interface PdlGateway : Gateway {
    fun hentPersoner(identer: List<String>): List<PdlPerson>
    fun hentIdenter(ident: String): List<PdlIdent>
}

internal data class PdlRequest(val query: String, val variables: Variables) {
    data class Variables(val ident: String? = null, val identer: List<String>? = null)

    companion object {
        fun hentPersonBolk(personidenter: List<String>) = PdlRequest(
            query = PERSON_BOLK_QUERY,
            variables = Variables(identer = personidenter),
        )

        fun hentIdenter(ident: String) = PdlRequest(
            query = IDENT_QUERY,
            variables = Variables(ident = ident),
        )
    }
}

data class PdlRespons(val hentPersonBolk: List<HentPersonBolkResult>)

data class HentPersonBolkResult(val ident: String, val person: PdlPerson?)

data class PdlIdenterRespons(val hentIdenter: PdlIdenter?)

data class PdlIdenter(val identer: List<PdlIdent>)

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String = "FOLKEREGISTERIDENT",
)

val PERSON_BOLK_QUERY = $$"""
    query($identer: [ID!]!) {
        hentPersonBolk(identer: $identer) {
            ident,
            person {
                adressebeskyttelse {
                    gradering
                },
            }
            code
        }
    }
""".trimIndent()

val IDENT_QUERY = $$"""
    query($ident: ID!) {
        hentIdenter(ident: $ident, historikk: true, grupper: [FOLKEREGISTERIDENT]) {
            identer {
                ident,
                historisk,
                gruppe
            }
        }
    }
""".trimIndent()

data class PdlPerson(val adressebeskyttelse: List<Adressebeskyttelse>)

data class Adressebeskyttelse(
    val gradering: Gradering?
)

@Suppress("unused")
enum class Gradering {
    FORTROLIG, STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG, UGRADERT
}
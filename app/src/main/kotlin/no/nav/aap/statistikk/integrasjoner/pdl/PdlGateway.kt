package no.nav.aap.statistikk.integrasjoner.pdl

import no.nav.aap.komponenter.gateway.Gateway
import no.nav.aap.statistikk.person.Ident


interface PdlGateway : Gateway {
    fun hentPersoner(identer: List<Ident>): List<PdlPerson>
    fun hentIdenter(ident: Ident): List<PdlIdent>
}

internal data class PdlRequest(val query: String, val variables: Variables) {
    data class Variables(val ident: String? = null, val identer: List<String>? = null)

    companion object {
        fun hentPersonBolk(personidenter: List<Ident>) = PdlRequest(
            query = PERSON_BOLK_QUERY,
            variables = Variables(identer = personidenter.map { it.ident }),
        )

        fun hentIdenter(ident: Ident) = PdlRequest(
            query = IDENT_QUERY,
            variables = Variables(ident = ident.ident),
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
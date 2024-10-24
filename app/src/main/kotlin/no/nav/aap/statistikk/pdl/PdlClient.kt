package no.nav.aap.statistikk.pdl


interface PdlClient {
    fun hentPerson(ident: String): Person
}

data class Person(val adressebeskyttelse: Adressebeskyttelse)

data class Adressebeskyttelse(
    val gradering: Gradering
)

enum class Gradering {
    FORTROLIG,
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    UGRADERT
}
package no.nav.aap.statistikk.pdl

class SkjermingService(
    private val pdlClient: PdlClient
) {

    fun erSkjermet(identer: List<String>): Boolean {
        val hentPersoner = pdlClient.hentPersoner(identer)

        return hentPersoner.any { it.adressebeskyttelse.gradering.erHemmelig() }
    }
}

fun Gradering.erHemmelig(): Boolean {
    return this == Gradering.STRENGT_FORTROLIG || this == Gradering.STRENGT_FORTROLIG_UTLAND
}
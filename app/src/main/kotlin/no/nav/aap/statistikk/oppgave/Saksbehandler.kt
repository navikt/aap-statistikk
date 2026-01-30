package no.nav.aap.statistikk.oppgave

data class Saksbehandler(val id: Long? = null, val ident: String) {
    constructor(ident: String) : this(ident = ident, id = null)
}
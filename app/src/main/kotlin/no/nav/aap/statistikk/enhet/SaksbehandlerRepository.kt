package no.nav.aap.statistikk.enhet

import no.nav.aap.komponenter.repository.Repository
import no.nav.aap.statistikk.oppgave.Saksbehandler

interface SaksbehandlerRepository : Repository {
    fun lagreSaksbehandler(saksbehandler: Saksbehandler): Long
    fun hentSaksbehandler(ident: String): Saksbehandler?
}
package no.nav.aap.statistikk.enhet

import no.nav.aap.komponenter.repository.Repository

interface EnhetRepository : Repository {
    fun lagreEnhet(enhet: Enhet): Long
    fun hentEnhet(kode: String): Enhet?
}
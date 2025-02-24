package no.nav.aap.statistikk.postmottak

import java.util.*

class PostmottakBehandlingService(private val postmottakBehandlingRepository: PostmottakBehandlingRepository) {
    fun oppdaterEllerOpprettBehandling(innkommendeBehandling: PostmottakBehandling): PostmottakBehandling {
        val eksisterendeBehandling =
            postmottakBehandlingRepository.hentEksisterendeBehandling(innkommendeBehandling.referanse)

        return if (eksisterendeBehandling == null) {
            val id =
                postmottakBehandlingRepository.opprettBehandling(behandling = innkommendeBehandling)
            innkommendeBehandling.medId(id = id)
        } else {
            postmottakBehandlingRepository.oppdaterBehandling(
                innkommendeBehandling.referanse,
                behandling = innkommendeBehandling.endringer().first() // "Only"
            )
            postmottakBehandlingRepository.hentEksisterendeBehandling(innkommendeBehandling.referanse)!!
        }
    }
}
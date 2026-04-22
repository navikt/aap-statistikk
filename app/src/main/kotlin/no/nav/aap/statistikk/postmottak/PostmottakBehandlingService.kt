package no.nav.aap.statistikk.postmottak

import no.nav.aap.statistikk.only

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
                behandling = innkommendeBehandling.endringer().only()
            )
            requireNotNull(postmottakBehandlingRepository.hentEksisterendeBehandling(innkommendeBehandling.referanse)) {
                "Behandling med referanse ${innkommendeBehandling.referanse} forsvant etter oppdatering."
            }
        }
    }
}
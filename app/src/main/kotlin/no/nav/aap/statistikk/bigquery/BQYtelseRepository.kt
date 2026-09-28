package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.behandling.BehandlingTabell
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(BQYtelseRepository::class.java)

class BQYtelseRepository(
    private val client: IBigQueryClient
) : IBQYtelsesstatistikkRepository {
    private val behandlingTabell = BehandlingTabell()

    override fun lagre(payload: BQYtelseBehandling) {
        logger.info("Lagrer BQYtelseBehandling for behandling ${payload.referanse}.")
        client.insert(behandlingTabell, payload)
    }

    override fun toString(): String {
        return "BQRepository(behandlingTabell=$behandlingTabell, client=$client)"
    }
}

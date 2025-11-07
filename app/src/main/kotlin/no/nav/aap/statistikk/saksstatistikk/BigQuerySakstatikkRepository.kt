package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(BigQuerySakstatikkRepository::class.java)

@Deprecated("Vil erstattes av lagring+replikering.")
class BigQuerySakstatikkRepository(
    private val client: BigQueryClient
) : IBQSakstatistikkRepository {
    private val sakTabell = SakTabell()

    override fun lagre(payload: BQBehandling) {
        logger.info("Lagrer saksinfo til BigQuery for saksnr ${payload.saksnummer}")
        client.insert(sakTabell, payload)
    }

    override fun toString(): String {
        return "BigQuerySakstatikkRepository(client=$client, sakTabell=$sakTabell)"
    }
}
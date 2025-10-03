package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.IBQSakstatistikkRepository
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger(BigQuerySakstatikkRepository::class.java)

class BigQuerySakstatikkRepository
    (
    private val client: BigQueryClient
) : IBQSakstatistikkRepository {
    private val sakTabell = SakTabell()

    override fun lagre(payload: BQBehandling) {
        logger.info("Lagrer saksinfo til BigQuery for saksnr ${payload.saksnummer}")
        client.insert(sakTabell, payload)
    }

    override fun hentNyesteForBehandling(behandlingReferanse: UUID): BQBehandling? {
        return client.read(
            sakTabell,
            "behandlingUuid = '$behandlingReferanse' order by tekniskTid limit 1"
        ).firstOrNull()
    }

    override fun toString(): String {
        return "BigQuerySakstatikkRepository(client=$client, sakTabell=$sakTabell)"
    }
}
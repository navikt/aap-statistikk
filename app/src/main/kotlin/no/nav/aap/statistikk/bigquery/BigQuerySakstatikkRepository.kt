package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.SakTabell
import java.util.UUID

private val logger = org.slf4j.LoggerFactory.getLogger(BigQuerySakstatikkRepository::class.java)

class BigQuerySakstatikkRepository
    (
    private val client: BigQueryClient
) : IBQSakstatistikkRepository {
    private val sakTabell = SakTabell()

    override fun lagre(payload: BQBehandling) {
        logger.info("Lagrer saksinfo.")
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
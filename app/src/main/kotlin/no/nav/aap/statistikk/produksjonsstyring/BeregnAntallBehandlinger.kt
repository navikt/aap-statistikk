package no.nav.aap.statistikk.produksjonsstyring

import java.time.LocalDate

data class AntallBehandlinger(val nye: Int = 0, val avsluttede: Int = 0, val totalt: Int = 0)

object BeregnAntallBehandlinger {

    fun antallBehandlingerPerDag(
        antallNye: List<AntallPerDag>,
        antallAvsluttede: List<AntallPerDag>,
        antallÅpneBehandlinger: Int
    ): Map<LocalDate, AntallBehandlinger> {
        val antallBehandlinger = mutableMapOf<LocalDate, AntallBehandlinger>()

        // Finn start og slutt
        val alleDagerMedEndringer =
            antallNye.map { it.dag }.plus(antallAvsluttede.map { it.dag }.toSet())
        val start = alleDagerMedEndringer.minOrNull() ?: LocalDate.now()
        val slutt = alleDagerMedEndringer.maxOrNull() ?: LocalDate.now()

        // Opprett AntallBehandlinger for alle dagene
        var dag = start
        while (dag <= slutt) {
            antallBehandlinger[dag] = AntallBehandlinger()
            dag = dag.plusDays(1)
        }

        // Oppdater AntallBehandlinger med nye og avsluttede behandlinger
        antallNye.forEach {
            antallBehandlinger[it.dag] = antallBehandlinger[it.dag]!!.copy(nye = it.antall)
        }
        antallAvsluttede.forEach {
            antallBehandlinger[it.dag] = antallBehandlinger[it.dag]!!.copy(avsluttede = it.antall)
        }

        // Oppdater AntallBehandlinger med totalt antall behandlinger per dag
        var justertAntallBehandlinger = antallÅpneBehandlinger
        antallBehandlinger.keys.sorted().reversed().forEach {
            val antall = antallBehandlinger[it]
            require(antall != null)
            antallBehandlinger[it] = antall.copy(totalt = justertAntallBehandlinger)
            justertAntallBehandlinger -= antall.nye
            justertAntallBehandlinger += antall.avsluttede
        }

        return antallBehandlinger
    }

}
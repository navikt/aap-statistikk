package no.nav.aap.statistikk.produksjonsstyring

import java.time.LocalDate

data class AntallBehandlinger(val nye: Int = 0, val avsluttede: Int = 0, val totalt: Int = 0)

object BeregnAntallBehandlinger {

    fun antallBehandlingerPerDag(antallNye: List<AntallPerDag>, antallAvsluttede: List<AntallPerDag>, antallÅpneBehandlinger: Int): Map<LocalDate, AntallBehandlinger> {
        val antallBehandlinger = mutableMapOf<LocalDate, AntallBehandlinger>()

        antallNye.forEach { antallBehandlinger[it.dag] = AntallBehandlinger(nye = it.antall) }
        antallAvsluttede.forEach {
            if (antallBehandlinger[it.dag] != null) {
                antallBehandlinger[it.dag] =
                    antallBehandlinger[it.dag]!!.copy(avsluttede = it.antall)
            } else {
                antallBehandlinger[it.dag] = AntallBehandlinger(avsluttede = it.antall)
            }
        }
        var justertAntallBehandlinger = antallÅpneBehandlinger
        antallBehandlinger.keys.sorted().reversed().forEach {
            var antall = antallBehandlinger[it]
            if (antall != null) {
                antall = antall.copy(totalt = justertAntallBehandlinger)
                justertAntallBehandlinger -= antall.nye
                justertAntallBehandlinger += antall.avsluttede
                antallBehandlinger[it] = antall
            }
        }

        return antallBehandlinger.toMap()
    }

}
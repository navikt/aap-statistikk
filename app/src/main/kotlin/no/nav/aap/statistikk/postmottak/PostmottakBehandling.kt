package no.nav.aap.statistikk.postmottak

import no.nav.aap.statistikk.behandling.KildeSystem
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.person.Person
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

data class PostmottakBehandling(
    private var id: Long? = null,
    val journalpostId: Long,
    val person: Person,
    val referanse: UUID,
    val behandlingType: TypeBehandling,
    val mottattTid: LocalDateTime,
    private val endringer: MutableList<PostmottakOppdatering> = mutableListOf(),
) {
    init {
        require(behandlingType.kildeSystem == KildeSystem.Postmottak)
        require(endringer.count { it.gjeldende } <= 1)
    }

    internal fun medId(id: Long): PostmottakBehandling {
        this.id = id
        return this
    }

    fun id() = id

    fun settInnEndring(endring: PostmottakOppdatering) {
        this.endringer.plus(endring)
    }

    fun medEndringer(endringer: List<PostmottakOppdatering>): PostmottakBehandling {
        this.endringer.addAll(endringer)
        return this
    }

    fun status(): String? {
        return this.endringer.find { it.gjeldende }?.status
    }

    /**
     * Få et ikke-muterbar view av endringer-listen.
     */
    fun endringer() = endringer.toList()
}

data class PostmottakOppdatering(
    val gjeldende: Boolean,
    val status: String,
    val oppdatertTid: LocalDateTime,
    val sisteSaksbehandler: String?,
    val gjeldendeAvklaringsBehov: String?,
)
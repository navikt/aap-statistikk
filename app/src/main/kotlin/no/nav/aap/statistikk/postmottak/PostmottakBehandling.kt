package no.nav.aap.statistikk.postmottak

import no.nav.aap.statistikk.behandling.KildeSystem
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.person.Person
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class PostmottakBehandling(
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
        require(endringer.filter { it.gjeldende }.size <= 1)
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

    /**
     * Få et ikke-muterbar view av endringer-listen.
     */
    fun endringer() = endringer.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PostmottakBehandling

        if (journalpostId != other.journalpostId) return false
        if (person != other.person) return false
        if (referanse != other.referanse) return false
        if (behandlingType != other.behandlingType) return false
        if (mottattTid.truncatedTo(ChronoUnit.MILLIS) != other.mottattTid.truncatedTo(ChronoUnit.MILLIS)) return false
        if (endringer != other.endringer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = journalpostId.hashCode()
        result = 31 * result + person.hashCode()
        result = 31 * result + referanse.hashCode()
        result = 31 * result + behandlingType.hashCode()
        result = 31 * result + mottattTid.truncatedTo(ChronoUnit.MILLIS).hashCode()
        result = 31 * result + endringer.hashCode()
        return result
    }

    override fun toString(): String {
        return "PostmottakBehandling(behandlingType=$behandlingType, id=$id, journalpostId=$journalpostId, person=$person, referanse=$referanse, mottattTid=$mottattTid, endringer=$endringer)"
    }
}

class PostmottakOppdatering(
    val gjeldende: Boolean,
    val status: String,
    val oppdatertTid: LocalDateTime,
    val sisteSaksbehandler: String?,
    val gjeldendeAvklaringsBehov: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PostmottakOppdatering

        if (gjeldende != other.gjeldende) return false
        if (status != other.status) return false
        if (oppdatertTid.truncatedTo(ChronoUnit.MILLIS) != other.oppdatertTid.truncatedTo(ChronoUnit.MILLIS)) return false
        if (sisteSaksbehandler != other.sisteSaksbehandler) return false
        if (gjeldendeAvklaringsBehov != other.gjeldendeAvklaringsBehov) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gjeldende.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + oppdatertTid.truncatedTo(ChronoUnit.MILLIS).hashCode()
        result = 31 * result + (sisteSaksbehandler?.hashCode() ?: 0)
        result = 31 * result + (gjeldendeAvklaringsBehov?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PostmottakOppdatering(gjeldende=$gjeldende, status='$status', oppdatertTid=$oppdatertTid, sisteSaksbehandler=$sisteSaksbehandler, gjeldendeAvklaringsBehov=$gjeldendeAvklaringsBehov)"
    }
}
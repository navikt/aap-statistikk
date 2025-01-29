package no.nav.aap.statistikk.person

/**
 * Representerer en person som har søkt om ytelse.
 */
class Person(val ident: String, private var id: Long? = null) {
    fun id() = id
    fun settId(id: Long) {
        this.id = id
    }

    fun medId(id: Long): Person {
        this.id = id
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Person

        return id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "Person(id=$id, ident=XXXX)"
    }
}
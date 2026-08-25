package no.nav.aap.statistikk.person

@JvmInline
value class Ident(val ident: String) {
    @Override
    override fun toString(): String {
        return "XXX"
    }
}

/**
 * Representerer en person som har søkt om ytelse.
 */
class Person(val ident: Ident, private val skjermet: Boolean = false, private var id: Long? = null) {
    fun id() = id
    fun erSkjermet() = skjermet
    fun settId(id: Long) {
        this.id = id
    }

    fun medId(id: Long): Person {
        this.id = id
        return this
    }

    override fun toString(): String {
        return "Person(id=$id, ident=XXXX)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Person

        if (skjermet != other.skjermet) return false
        if (id != other.id) return false
        if (ident != other.ident) return false

        return true
    }

    override fun hashCode(): Int {
        var result = skjermet.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + ident.hashCode()
        return result
    }
}
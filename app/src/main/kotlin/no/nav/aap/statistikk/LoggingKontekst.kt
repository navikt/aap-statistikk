package no.nav.aap.statistikk

import org.slf4j.MDC
import java.io.Closeable
import java.util.*

class LoggingKontekst(val referanse: UUID) : Closeable {
    private val keys = HashSet<String>()

    init {
        keys.add("referanse")
        MDC.put("referanse", referanse.toString())
    }

    override fun close() {
        keys.forEach { MDC.remove(it) }
    }
}
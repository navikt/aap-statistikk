package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import java.util.*

val logger = LoggerFactory.getLogger("no.nav.aap.statistikk")

fun main() {
    logger.info("I started :).")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.info("")
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    install(ContentNegotiation) {
        // TODO sjekk om bør gjøre samme settings som behandlingsflyt
        register(ContentType.Application.Json, JacksonConverter())
        jackson { disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) }
    }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        callIdMdc("callId")
        filter { call -> call.request.path().startsWith("/actuator").not() }
    }

    routing {
        route("/actuator") {
            get("/metrics") {
                call.respond(prometheus.scrape())
            }
            get("/live") {
                call.respond(HttpStatusCode.OK, "live")
            }
            get("/ready") {
                call.respond(HttpStatusCode.OK, "ready")
            }
        }
    }
}

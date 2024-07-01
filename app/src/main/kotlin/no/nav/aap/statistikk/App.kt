package no.nav.aap.statistikk

import com.fasterxml.jackson.databind.SerializationFeature
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
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
import no.nav.aap.statistikk.api.HendelsesRepository
import no.nav.aap.statistikk.api.MottaStatistikkDTO
import no.nav.aap.statistikk.api.mottaStatistikk
import org.slf4j.LoggerFactory
import java.util.*

val logger = LoggerFactory.getLogger("no.nav.aap.statistikk")

fun main() {
    logger.info("I started :).")
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> logger.error("Uhåndtert feil", e) }


    val hendelsesRepository = object : HendelsesRepository {
        override fun lagreHendelse(hendelse: MottaStatistikkDTO) {
            logger.info("Skrev hendelse.")
        }
    }

    embeddedServer(Netty, port = 8080) {
        module(hendelsesRepository)
    }.start(wait = true)
}

fun Application.module(hendelsesRepository: HendelsesRepository) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.info("Noe gikk galt. %", cause)
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
        // jackson { disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) }
    }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XCorrelationId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        callIdMdc("callId")
        filter { call -> call.request.path().startsWith("/actuator").not() }
    }

    install(OpenAPIGen) {
        // this serves OpenAPI definition on /openapi.json
        serveOpenApiJson = true
        // this servers Swagger UI on /swagger-ui/index.html
        serveSwaggerUi = true
        info {
            title = "AAP - Saksbehandling"
        }
    }

    routing {
        apiRouting {
            mottaStatistikk(hendelsesRepository)
        }
        route("/") {
            get {
                call.respondText("Hello World!", contentType = ContentType.Text.Plain)
            }
        }
        route("/actuator") {
            get("/metrics") {
                call.respond(prometheus.scrape())
            }
            get("/live") {
                // TODO: logic here
                // https://www.reddit.com/r/kubernetes/comments/wayj42/what_should_readiness_liveness_probe_actually/
                call.respond(HttpStatusCode.OK, "live")
            }
            get("/ready") {
                // TODO: logic here
                call.respond(HttpStatusCode.OK, "ready")
            }
        }
    }

}

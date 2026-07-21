package no.nav.aap.statistikk

import com.papsign.ktor.openapigen.model.info.InfoModel
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.mockk.mockk
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.komponenter.server.auth.IdentityProvider
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.statistikk.testutils.Fakes
import no.nav.aap.statistikk.testutils.MockJobbAppender
import no.nav.aap.statistikk.testutils.noOpTransactionExecutor
import java.io.BufferedWriter
import java.io.FileWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*

fun main() {
    val texasFake = Fakes.TexasFake(port = 8081)
    texasFake.start()

    System.setProperty("NAIS_TOKEN_ENDPOINT", "http://localhost:8081/token")
    System.setProperty("NAIS_TOKEN_EXCHANGE_ENDPOINT", "http://localhost:8081/token/exchange")
    System.setProperty("NAIS_TOKEN_INTROSPECTION_ENDPOINT", "http://localhost:8081/introspect")

    val randomUUID = UUID.randomUUID()
    System.setProperty("integrasjon.postmottak.azp", randomUUID.toString())
    System.setProperty("integrasjon.oppgave.azp", randomUUID.toString())
    System.setProperty("integrasjon.behandlingsflyt.azp", randomUUID.toString())

    val server = embeddedServer(Netty, port = 8080) {
        commonKtorModule(
            PrometheusProvider.prometheus,
            InfoModel(title = "AAP - Statistikk", version = "0.0.1"),
            identityProvider = IdentityProvider.ENTRA_ID
        )
        module(
            transactionExecutor = noOpTransactionExecutor,
            jobbAppender = MockJobbAppender(),
            motorApiCallback = { },
            lagreStoppetHendelseJobb = mockk(),
            lagreAvklaringsbehovHendelseJobb = mockk(),
        )
    }.start()

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = AzureM2MTokenProvider,
        responseHandler = DefaultResponseHandler()
    )

    val resp = restClient.get(
        URI.create("http://localhost:8080/openapi.json"),
        request = GetRequest(),
        mapper = { body, _ -> String(body.readAllBytes(), StandardCharsets.UTF_8) }
    )!!


    val writer = BufferedWriter(FileWriter("openapi.json"))
    writer.use {
        it.write(resp)
    }

    server.stop()
}

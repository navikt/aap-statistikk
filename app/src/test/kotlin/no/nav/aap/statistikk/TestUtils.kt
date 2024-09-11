package no.nav.aap.statistikk

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.Motor
import no.nav.aap.statistikk.api_kontrakt.MottaStatistikkDTO
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.server.authenticate.AzureConfig
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BigQueryEmulatorContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("TestUtils")

/**
 * @param azureConfig Send inn egen her om det skal gjøres autentiserte kall.
 */
fun <E> testKlient(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    jobbAppender: JobbAppender,
    avsluttetBehandlingService: AvsluttetBehandlingService,
    azureConfig: AzureConfig = AzureConfig(
        clientId = "tilgang",
        jwks = URI.create("http://localhost:8081/jwks").toURL(),
        issuer = "tilgang"
    ),
    test: suspend (HttpClient) -> E?
): E? {
    var res: E? = null;

    testApplication {
        application {
            module(
                transactionExecutor,
                motor,
                jobbAppender,
                avsluttetBehandlingService,
                azureConfig
            )
        }
        val client = client.config {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                }
            }
        }

        res = test(client)
    }
    return res
}

fun postgresTestConfig(port: Int? = null): DbConfig {
    val postgres = PostgreSQLContainer("postgres:16")
    if (port != null) {
        postgres.portBindings = listOf("5432:5432")
    }
    postgres.waitingFor(
        HostPortWaitStrategy().withStartupTimeout(
            Duration.of(
                60L,
                ChronoUnit.SECONDS
            )
        )
    )
    postgres.start()

    val dbConfig = DbConfig(
        jdbcUrl = postgres.jdbcUrl,
        userName = postgres.username,
        password = postgres.password
    )
    return dbConfig
}


fun bigQueryContainer(): BigQueryConfig {
    val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.3");
    container.start()

    val url = container.emulatorHttpEndpoint

    val datasetInfo = DatasetInfo.newBuilder("my-dataset").build()
    val config = object : BigQueryConfig {
        override val dataset: String
            get() = "my-dataset"

        override fun bigQueryOptions(): BigQueryOptions {
            return BigQueryOptions.newBuilder()
                .setLocation(url)
                .setProjectId(container.projectId)
                .setHost(url)
                .setCredentials(NoCredentials.getInstance())
                .build()
        }
    }

    logger.info("BigQuery URL: $url")

    val bigQueryOptions: BigQueryOptions = config.bigQueryOptions()
    val bigQuery: BigQuery = bigQueryOptions.service

    // Lag datasett
    bigQuery.create(datasetInfo)

    return config
}

fun opprettTestHendelse(dataSource: DataSource, randomUUID: UUID, saksnummer: String) {
    dataSource.transaction { conn ->
        val hendelse = HendelsesRepository(conn)
        hendelse.lagreHendelse(
            MottaStatistikkDTO(
                saksnummer = saksnummer,
                behandlingReferanse = randomUUID,
                behandlingOpprettetTidspunkt = LocalDateTime.now(),
                status = "IVERKSATT",
                behandlingType = TypeBehandling.Førstegangsbehandling,
                ident = "123",
                avklaringsbehov = listOf()
            )
        )
    }
}

val noOpTransactionExecutor = object : TransactionExecutor {
    override fun <E> withinTransaction(block: (DBConnection) -> E): E {
        return block(mockk())
    }
}

fun motorMock(): Motor {
    val motor = mockk<Motor>()
    every { motor.start() } just Runs
    return motor
}


class MockJobbAppender : JobbAppender {
    var jobber = mutableListOf<JobbInput>()

    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        jobber.add(jobb)
    }

    override fun leggTil(jobb: JobbInput) {
        jobber.add(jobb)
    }
}

class FakeBQRepository : IBQRepository {
    val vilkårsresultater = mutableListOf<Vilkårsresultat>()
    val tilkjentYtelse = mutableListOf<TilkjentYtelse>()
    val beregningsgrunnlag = mutableListOf<IBeregningsGrunnlag>()

    override fun lagre(payload: Vilkårsresultat) {
        vilkårsresultater.add(payload)
    }

    override fun lagre(payload: TilkjentYtelse) {
        tilkjentYtelse.add(payload)
    }

    override fun lagre(
        payload: IBeregningsGrunnlag,
        behandlingsReferanse: UUID
    ) {
        beregningsgrunnlag.add(payload)
    }
}

class FakeTilkjentYtelseRepository : ITilkjentYtelseRepository {
    val tilkjentYtelser = mutableMapOf<Int, TilkjentYtelseEntity>()
    override fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Long {
        tilkjentYtelser.put(tilkjentYtelser.size, tilkjentYtelse)
        return (tilkjentYtelser.size + 1).toLong();
    }

    override fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse? {
        TODO("Not yet implemented")
    }

}
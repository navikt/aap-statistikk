package no.nav.aap.statistikk.testutils

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.Motor
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.StoppetBehandling
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.avsluttetbehandling.IAvsluttetBehandlingRepository
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagBQ
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.BigQueryClient
import no.nav.aap.statistikk.bigquery.BigQueryConfig
import no.nav.aap.statistikk.bigquery.IBQRepository
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository
import no.nav.aap.statistikk.hendelser.repository.IHendelsesRepository
import no.nav.aap.statistikk.jobber.LagreAvsluttetBehandlingDTOJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.module
import no.nav.aap.statistikk.person.IPersonRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.*
import no.nav.aap.statistikk.startUp
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BigQueryEmulatorContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.io.InputStream
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

private val logger = LoggerFactory.getLogger("TestUtils")

/**
 * @param azureConfig Send inn egen her om det skal gjøres autentiserte kall.
 */
fun <E> testKlient(
    transactionExecutor: TransactionExecutor,
    motor: Motor,
    jobbAppender: JobbAppender,
    lagreAvsluttetBehandlingDTOJobb: LagreAvsluttetBehandlingDTOJobb,
    azureConfig: AzureConfig = AzureConfig(
        clientId = "tilgang",
        jwksUri = "http://localhost:8081/jwks",
        issuer = "tilgang",
        tokenEndpoint = URI.create("http://localhost:8081/jwks"),
        clientSecret = "xxx",
    ),
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    test: (url: String, client: RestClient<InputStream>) -> E?,
): E? {
    val res: E?;

    System.setProperty("azure.openid.config.token.endpoint", azureConfig.tokenEndpoint.toString())
    System.setProperty("azure.app.client.id", azureConfig.clientId)
    System.setProperty("azure.app.client.secret", azureConfig.clientSecret)
    System.setProperty("azure.openid.config.jwks.uri", azureConfig.jwksUri)
    System.setProperty("azure.openid.config.issuer", azureConfig.issuer)

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler()
    )

    val server = embeddedServer(Netty, port = 0) {
        module(
            transactionExecutor,
            motor,
            jobbAppender,
            lagreAvsluttetBehandlingDTOJobb,
            azureConfig,
            {

            },
            lagreStoppetHendelseJobb,
            PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        )
    }.start()

    val port = runBlocking { server.resolvedConnectors().first().port }

    res = test("http://localhost:$port", restClient)

    server.stop(500L, 10_000L)

    return res
}

fun <E> testKlientNoInjection(
    dbConfig: DbConfig,
    azureConfig: AzureConfig = AzureConfig(
        clientId = "tilgang",
        jwksUri = "http://localhost:8081/jwks",
        issuer = "tilgang"
    ),
    bigQueryClient: BigQueryClient,
    test: (url: String, client: RestClient<InputStream>) -> E?,
): E? {
    var res: E? = null;

    System.setProperty("azure.openid.config.token.endpoint", azureConfig.tokenEndpoint.toString())
    System.setProperty("azure.app.client.id", azureConfig.clientId)
    System.setProperty("azure.app.client.secret", azureConfig.clientSecret)
    System.setProperty("azure.openid.config.jwks.uri", azureConfig.jwksUri)
    System.setProperty("azure.openid.config.issuer", azureConfig.issuer)

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler()
    )

    val server = embeddedServer(Netty, port = 0) {
        startUp(
            dbConfig,
            azureConfig,
            bigQueryClient
        )
    }.start()

    val port = runBlocking { server.resolvedConnectors().first().port }

    res = test("http://localhost:$port", restClient)

    server.stop(500L, 10_000L)

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

fun opprettTestHendelse(
    dataSource: DataSource,
    randomUUID: UUID,
    saksnummer: String
): Pair<BehandlingId, SakId> {
    val ident = "29021946"

    val id = opprettTestPerson(dataSource, ident)

    val sak = opprettTestSak(dataSource, saksnummer, Person(ident, id = id))

    val behandling = opprettTestBehandling(
        dataSource,
        randomUUID,
        Sak(
            id = sak.id,
            saksnummer = saksnummer,
            person = Person(ident, id = id),
        ),
    )

    val sakId = sak.id!!
    val behandlingId = behandling.id!!
    dataSource.transaction { conn ->
        val hendelseRepo = HendelsesRepository(conn)
        val hendelse = StoppetBehandling(
            saksnummer = saksnummer,
            behandlingReferanse = randomUUID,
            behandlingOpprettetTidspunkt = LocalDateTime.now(),
            status = BehandlingStatus.UTREDES,
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = ident,
            avklaringsbehov = listOf(),
            versjon = UUID.randomUUID().toString()
        )
        hendelseRepo.lagreHendelse(
            hendelse, sakId, behandlingId
        )
    }

    return Pair(behandlingId, sakId)
}

fun opprettTestPerson(dataSource: DataSource, ident: String): Long {
    return dataSource.transaction { conn ->
        val personRepository = PersonRepository(conn)
        personRepository.hentPerson(ident)?.id ?: personRepository.lagrePerson(Person(ident))
    }
}

fun opprettTestSak(dataSource: DataSource, saksnummer: String, person: Person): Sak {
    return dataSource.transaction {
        val sak = Sak(
            saksnummer = saksnummer,
            person = person,
            id = null
        )
        val id = SakRepositoryImpl(it).settInnSak(sak)
        sak.copy(id)
    }
}

fun opprettTestBehandling(dataSource: DataSource, referanse: UUID, sak: Sak): Behandling {
    return dataSource.transaction {
        val behandling = Behandling(
            referanse = referanse,
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            opprettetTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
        )
        val id = BehandlingRepository(it).lagre(
            behandling
        )

        behandling.copy(id)
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

class FakeSakRepository : SakRepository {
    private val saker = mutableMapOf<Long, Sak>()
    override fun hentSak(sakID: SakId): Sak {
        saker[sakID]?.let { return it }
        throw IllegalArgumentException("Fant ikke sak med id $sakID")
    }

    override fun hentSak(saksnummer: String): Sak {
        return saker.values.firstOrNull { it.saksnummer == saksnummer }!!
    }

    override fun hentSakEllernull(saksnummer: String): Sak? {
        return saker.values.firstOrNull { it.saksnummer == saksnummer }
    }

    override fun settInnSak(sak: Sak): SakId {
        val id = saker.size.toLong()
        saker[id] = sak.copy(id = id)
        return id
    }

    override fun tellSaker(): Int {
        return saker.size
    }
}

class FakePersonRepository : IPersonRepository {
    private val personer = mutableMapOf<Long, Person>()
    override fun lagrePerson(person: Person): Long {
        personer[personer.size.toLong()] = person
        return (personer.size - 1).toLong()
    }

    override fun hentPerson(ident: String): Person? {
        return personer.values.firstOrNull { it.ident == ident }
    }
}

class FakeHendelsesRepository : IHendelsesRepository {
    private val hendelser = mutableListOf<StoppetBehandling>()

    override fun lagreHendelse(
        hendelse: StoppetBehandling,
        sakId: SakId,
        behandlingId: BehandlingId
    ): Int {
        hendelser.add(hendelse)
        return hendelser.indexOf(hendelse)
    }

    override fun hentHendelser(): Collection<StoppetBehandling> {
        return hendelser
    }

    override fun tellHendelser(): Int {
        return hendelser.size
    }

}

class FakeBehandlingRepository : IBehandlingRepository {
    private val behandlinger = mutableListOf<Behandling>()
    override fun lagre(behandling: Behandling): Long {
        behandlinger.add(behandling)
        return behandlinger.indexOf(behandling).toLong()
    }

    override fun hent(referanse: UUID): Behandling? {
        val behandling = behandlinger.firstOrNull { it.referanse == referanse }
        if (behandling != null) {
            return behandling.copy(id = behandlinger.indexOf(behandling).toLong())
        }
        return null
    }

    override fun hent(id: Long): Behandling {
        return behandlinger[id.toInt()]
    }

    override fun hentEllerNull(id: Long): Behandling? {
        return behandlinger.getOrNull(id.toInt())
    }
}

class FakeBQRepository : IBQRepository {
    val vilkårsresultater = mutableListOf<Vilkårsresultat>()
    val tilkjentYtelse = mutableListOf<TilkjentYtelse>()
    val beregningsgrunnlag = mutableListOf<BeregningsGrunnlagBQ>()
    val saker = mutableListOf<BQBehandling>()

    override fun lagre(payload: Vilkårsresultat) {
        vilkårsresultater.add(payload)
    }

    override fun lagre(payload: TilkjentYtelse) {
        tilkjentYtelse.add(payload)
    }

    override fun lagre(
        payload: BeregningsGrunnlagBQ
    ) {
        beregningsgrunnlag.add(payload)
    }

    override fun lagre(payload: BQBehandling) {
        saker.add(payload)
    }
}

class FakeTilkjentYtelseRepository : ITilkjentYtelseRepository {
    private val tilkjentYtelser = mutableMapOf<Int, TilkjentYtelseEntity>()
    override fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Long {
        tilkjentYtelser.put(tilkjentYtelser.size, tilkjentYtelse)
        return (tilkjentYtelser.size - 1).toLong();
    }

    override fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse? {
        TODO("Not yet implemented")
    }
}

class FakeVilkårsResultatRepository : IVilkårsresultatRepository {
    val vilkår = mutableMapOf<Long, VilkårsResultatEntity>()

    override fun lagreVilkårsResultat(
        vilkårsresultat: VilkårsResultatEntity,
        behandlingId: Long
    ): Long {
        vilkår.put(vilkår.size.toLong(), vilkårsresultat)
        return (vilkår.size - 1).toLong()
    }

    override fun hentVilkårsResultat(vilkårResultatId: Long): VilkårsResultatEntity? {
        TODO("Not yet implemented")
    }
}

class FakeBeregningsgrunnlagRepository : IBeregningsgrunnlagRepository {
    val grunnlag = mutableListOf<MedBehandlingsreferanse<IBeregningsGrunnlag>>()
    override fun lagreBeregningsGrunnlag(beregningsGrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>): Long {
        grunnlag.add(beregningsGrunnlag)
        return grunnlag.indexOf(beregningsGrunnlag).toLong()
    }

    override fun hentBeregningsGrunnlag(): List<MedBehandlingsreferanse<IBeregningsGrunnlag>> {
        return grunnlag
    }
}

class FakeAvsluttetBehandlingDTORepository : IAvsluttetBehandlingRepository {
    val lagrede = mutableListOf<AvsluttetBehandlingDTO>()
    override fun lagre(behandling: AvsluttetBehandlingDTO): Long {
        lagrede.add(behandling)
        return lagrede.indexOf(behandling).toLong()
    }

    override fun hent(id: Long): AvsluttetBehandlingDTO {
        return lagrede.get(id.toInt())
    }
}

fun <E> ventPåSvar(getter: () -> E?, predicate: (E?) -> Boolean): E? {
    var res: E? = null;
    val timeInMillis = measureTimeMillis {
        val maxTid = LocalDateTime.now().plusSeconds(20)
        var suksess = false;
        while (maxTid.isAfter(LocalDateTime.now()) && !suksess) {
            try {
                res = getter()
                if (res != null && predicate(res)) {
                    suksess = true
                }
            } catch (e: Exception) {
                Thread.sleep(50L)
            }
        }
    }
    logger.info("Ventet på at prosessering skulle fullføre, det tok $timeInMillis millisekunder.")
    return res
}
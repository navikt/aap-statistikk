package no.nav.aap.statistikk.testutils

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.gateway.Factory
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.Motor
import no.nav.aap.statistikk.avsluttetbehandling.*
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.bigquery.*
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.defaultGatewayProvider
import no.nav.aap.statistikk.integrasjoner.pdl.Adressebeskyttelse
import no.nav.aap.statistikk.integrasjoner.pdl.Gradering
import no.nav.aap.statistikk.integrasjoner.pdl.PdlGateway
import no.nav.aap.statistikk.jobber.LagreAvklaringsbehovHendelseJobb
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.meldekort.IMeldekortRepository
import no.nav.aap.statistikk.meldekort.Meldekort
import no.nav.aap.statistikk.module
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepositoryImpl
import no.nav.aap.statistikk.person.IPersonRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.sak.*
import no.nav.aap.statistikk.saksstatistikk.SaksStatistikkService
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepositoryImpl
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.startUp
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import org.slf4j.LoggerFactory
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.gcloud.BigQueryEmulatorContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.time.Clock
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
    azureConfig: AzureConfig = AzureConfig(
        clientId = "tilgang",
        jwksUri = "http://localhost:8081/jwks",
        issuer = "tilgang",
        tokenEndpoint = URI.create("http://localhost:8081/jwks"),
        clientSecret = "xxx",
    ),
    lagreStoppetHendelseJobb: LagreStoppetHendelseJobb,
    lagreOppgaveHendelseJobb: LagreOppgaveHendelseJobb,
    lagrePostmottakHendelseJobb: LagrePostmottakHendelseJobb,
    lagreAvklaringsbehovHendelseJobb: LagreAvklaringsbehovHendelseJobb,
    jobbAppender: JobbAppender,
    test: (url: String, client: RestClient<InputStream>) -> E?,
): E? {
    val res: E?

    System.setProperty("azure.openid.config.token.endpoint", azureConfig.tokenEndpoint.toString())
    System.setProperty("azure.app.client.id", azureConfig.clientId)
    System.setProperty("azure.app.client.secret", azureConfig.clientSecret)
    System.setProperty("azure.openid.config.jwks.uri", azureConfig.jwksUri)
    System.setProperty("azure.openid.config.issuer", azureConfig.issuer)
    val randomUUID = UUID.randomUUID()
    System.setProperty("integrasjon.postmottak.azp", randomUUID.toString())
    System.setProperty("integrasjon.oppgave.azp", randomUUID.toString())
    System.setProperty("integrasjon.behandlingsflyt.azp", randomUUID.toString())

    val restClient = RestClient(
        config = ClientConfig(scope = "AAP_SCOPES"),
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = DefaultResponseHandler(),
    )
    motor.start()

    val server = embeddedServer(Netty, port = 0) {
        module(
            transactionExecutor,
            jobbAppender,
            azureConfig,
            {},
            lagreStoppetHendelseJobb,
            lagreOppgaveHendelseJobb,
            lagrePostmottakHendelseJobb,
            lagreAvklaringsbehovHendelseJobb,
        )
    }.start()

    val port = runBlocking { server.engine.resolvedConnectors().first().port }

    res = test("http://localhost:$port", restClient)

    motor.stop()
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
    test: (url: String, client: RestClient<InputStream>) -> E,
): E {
    var res: E

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
            bigQueryClient,
            defaultGatewayProvider()
        )
    }.start()

    val port = runBlocking { server.engine.resolvedConnectors().first().port }

    res = test("http://localhost:$port", restClient)

    server.stop(500L, 10_000L)

    return res
}

fun postgresTestConfig(): DbConfig {
    val postgres = PostgreSQLContainer("postgres:16")
    // Get the current working directory
    val dumpFile = Path.of("").toAbsolutePath().resolve("dump.sql")

    val forHostPath = MountableFile.forHostPath(dumpFile)

    postgres.waitingFor(
        HostPortWaitStrategy().withStartupTimeout(
            Duration.of(
                60L,
                ChronoUnit.SECONDS
            )
        )
    ).withCopyFileToContainer(
        forHostPath,
        "/dump.sql"
    )
    postgres.start()

    if (dumpFile.toFile().exists()) {
        val res = postgres.execInContainer("bash", "-c", "psql -U test -d test -f /dump.sql 2>&1")
        if (res.exitCode != 0) {
            println(res)
        }
    } else {
        println("Kan ikke finne databasedump med path $dumpFile")
    }

    val dbConfig = DbConfig(
        jdbcUrl = postgres.jdbcUrl,
        userName = postgres.username,
        password = postgres.password
    )

    return dbConfig
}


fun bigQueryContainer(): BigQueryConfig {
    val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.3")
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
    saksnummer: Saksnummer,
    status: BehandlingStatus = BehandlingStatus.UTREDES,
    opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    vurderingsbehov: List<Vurderingsbehov> = emptyList(),
    clock: Clock = Clock.systemDefaultZone()
): Pair<BehandlingId, SakId> {
    val ident = "29021946"

    val personMedId = opprettTestPerson(dataSource, ident)

    val sak = opprettTestSak(dataSource, saksnummer, Person(ident, id = personMedId.id()))

    val behandling = opprettTestBehandling(
        dataSource,
        randomUUID,
        sak,
        status,
        opprettetTidspunkt,
        vurderingsbehov,
        clock
    )

    val sakId = sak.id!!
    val behandlingId = behandling.id()

    return Pair(behandlingId, sakId)
}

fun opprettTestPerson(dataSource: DataSource, ident: String): Person {
    return dataSource.transaction { conn ->
        val personRepository = PersonRepository(conn)
        PersonService(personRepository).hentEllerLagrePerson(ident)
    }
}

fun opprettTestSak(dataSource: DataSource, saksnummer: Saksnummer, person: Person): Sak {
    return dataSource.transaction {
        val sak = Sak(
            saksnummer = saksnummer,
            person = person,
            id = null,
            sistOppdatert = LocalDateTime.now(),
            sakStatus = SakStatus.UTREDES,
        )
        val id = SakRepositoryImpl(it).settInnSak(sak)
        sak.copy(id = id)
    }
}

fun opprettTestBehandling(
    dataSource: DataSource,
    referanse: UUID,
    sak: Sak,
    status: BehandlingStatus = BehandlingStatus.UTREDES,
    opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    vurderingsbehov: List<Vurderingsbehov> = emptyList(),
    clock: Clock = Clock.systemDefaultZone()
): Behandling {
    val behandling = Behandling(
        referanse = referanse,
        sak = sak,
        typeBehandling = TypeBehandling.Førstegangsbehandling,
        status = status,
        opprettetTid = opprettetTidspunkt,
        oppdatertTidspunkt = opprettetTidspunkt,
        mottattTid = opprettetTidspunkt.truncatedTo(ChronoUnit.SECONDS),
        versjon = Versjon(UUID.randomUUID().toString()),
        årsaker = vurderingsbehov,
        søknadsformat = SøknadsFormat.PAPIR,
    )
    return dataSource.transaction {
        val repo = BehandlingRepository(it, clock = clock)
        val uthentet = repo.hent(referanse)
        val id = if (uthentet != null) {
            repo.oppdaterBehandling(behandling.copy(id = uthentet.id))
            uthentet.id
        } else {
            BehandlingRepository(it, clock).opprettBehandling(
                behandling
            )
        }
        behandling.copy(id = id)
    }
}

val noOpTransactionExecutor = object : TransactionExecutor {
    override fun <E> withinTransaction(block: (DBConnection) -> E): E {
        return block(mockk(relaxed = true))
    }
}

fun motorMock(): Motor {
    val motor = mockk<Motor>()
    every { motor.start() } just Runs
    every { motor.stop() } just Runs
    return motor
}


class MockJobbAppender : JobbAppender {
    var jobber = mutableListOf<JobbInput>()
    private var bigQueryJobber = mutableListOf<BehandlingId>()

    override fun leggTil(
        connection: DBConnection,
        jobb: JobbInput
    ) {
        jobber.add(jobb)
    }

    override fun leggTil(
        repositoryProvider: RepositoryProvider,
        jobb: JobbInput
    ) {
        TODO("Not yet implemented")
    }

    override fun leggTilLagreSakTilBigQueryJobb(
        repositoryProvider: RepositoryProvider,
        behandlingId: BehandlingId,
        delayInSeconds: Long
    ) {
        logger.info("NO-OP: skal lagre til BigQuery for behandling $behandlingId.")
        bigQueryJobber.add(behandlingId)
    }

    override fun leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
        provider: RepositoryProvider,
        behandlingId: BehandlingId
    ) {
        TODO("Not yet implemented")
    }

    override fun leggTilResendSakstatistikkJobb(
        repositoryProvider: RepositoryProvider,
        behandlingId: BehandlingId
    ) {
        TODO("Not yet implemented")
    }
}

class FakeMeldekortRepository : IMeldekortRepository {
    private val meldekort = mutableMapOf<Long, List<Meldekort>>()
    override fun lagre(
        behandlingId: BehandlingId,
        meldekort: List<Meldekort>
    ) {
        this.meldekort[behandlingId.id] = meldekort
    }

    override fun hentMeldekort(behandlingId: BehandlingId): List<Meldekort> {
        return meldekort[behandlingId.id] ?: emptyList()
    }
}

class FakeSakRepository : SakRepository {
    private val saker = mutableMapOf<Long, Sak>()
    override fun hentSak(sakID: SakId): Sak {
        saker[sakID]?.let { return it }
        throw IllegalArgumentException("Fant ikke sak med id $sakID")
    }

    override fun hentSak(saksnummer: Saksnummer): Sak {
        return saker.values.firstOrNull { it.saksnummer == saksnummer }!!
    }

    override fun hentSakEllernull(saksnummer: Saksnummer): Sak? {
        return saker.values.firstOrNull { it.saksnummer == saksnummer }
            ?.also { requireNotNull(it.id) }
    }

    override fun settInnSak(sak: Sak): SakId {
        val id = saker.size.toLong()
        saker[id] = sak.copy(id = id)
        return id
    }

    override fun oppdaterSak(sak: Sak) {
        saker[sak.id!!] = sak.copy(sistOppdatert = LocalDateTime.now())
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

class FakeBehandlingRepository : IBehandlingRepository {
    private val behandlinger = mutableMapOf<Long, Behandling>()
    private var nextId = 0L
    override fun opprettBehandling(behandling: Behandling): BehandlingId {
        val id = nextId
        behandlinger[id] = behandling.copy(id = BehandlingId(id)).leggTilHendelse(
            BehandlingHendelse(
                tidspunkt = LocalDateTime.now(),
                hendelsesTidspunkt = LocalDateTime.now(),
                status = behandling.behandlingStatus(),
                avklaringsbehovStatus = behandling.gjeldendeAvklaringsbehovStatus,
                versjon = behandling.versjon,
                mottattTid = behandling.mottattTid,
                søknadsformat = behandling.søknadsformat,
            )
        )
        nextId++

        logger.info("Opprettet behandling med ID $id")
        return BehandlingId(id)
    }

    override fun oppdaterBehandling(behandling: Behandling) {
        logger.info("Oppdaterte behandling med ID ${behandling.id}")
        behandlinger[behandling.id?.id!!] = behandling.leggTilHendelse(
            BehandlingHendelse(
                tidspunkt = LocalDateTime.now(),
                hendelsesTidspunkt = LocalDateTime.now(),
                status = behandling.behandlingStatus(),
                avklaringsbehovStatus = behandling.gjeldendeAvklaringsbehovStatus,
                versjon = behandling.versjon,
                mottattTid = behandling.mottattTid,
                søknadsformat = behandling.søknadsformat
            )
        )
    }

    override fun invaliderOgLagreNyHistorikk(behandling: Behandling) {
        behandlinger[behandling.id?.id!!] = behandling
    }

    override fun hent(referanse: UUID): Behandling? {
        return behandlinger.asIterable().firstOrNull { it.value.referanse == referanse }?.value
    }

    override fun hent(id: BehandlingId): Behandling {
        return behandlinger[id.id]!!
    }

    override fun hentEllerNull(id: BehandlingId): Behandling? {
        return behandlinger[id.id]
    }
}

class FakeRettighetsTypeRepository : IRettighetstypeperiodeRepository {
    override fun lagre(
        behandlingReferanse: UUID,
        rettighetstypePeriode: List<RettighetstypePeriode>
    ) {
        TODO("Not yet implemented")
    }

    override fun hent(behandlingReferanse: UUID): List<RettighetstypePeriode> {
        return emptyList()
    }

}

class FakeDiagnoseRepository : DiagnoseRepository {
    override fun lagre(diagnoseEntity: DiagnoseEntity): Long {
        TODO("Not yet implemented")
    }

    override fun hentForBehandling(behandlingReferanse: UUID): DiagnoseEntity {
        TODO("Not yet implemented")
    }
}

class FakeBQYtelseRepository : IBQYtelsesstatistikkRepository {
    val behandlinger = mutableListOf<BQYtelseBehandling>()

    override fun lagre(payload: BQYtelseBehandling) {
        behandlinger.add(payload)
    }

    override fun commit() {

    }

    override fun start() {

    }
}

class FakeTilkjentYtelseRepository : ITilkjentYtelseRepository {
    private val tilkjentYtelser = mutableMapOf<Int, TilkjentYtelseEntity>()
    override fun lagreTilkjentYtelse(tilkjentYtelse: TilkjentYtelseEntity): Long {
        tilkjentYtelser[tilkjentYtelser.size] = tilkjentYtelse
        return (tilkjentYtelser.size - 1).toLong()
    }

    override fun hentTilkjentYtelse(tilkjentYtelseId: Int): TilkjentYtelse {
        TODO("Not yet implemented")
    }

    override fun hentForBehandling(behandlingId: UUID): TilkjentYtelse {
        TODO("Not yet implemented")
    }
}

class FakeVilkårsResultatRepository : IVilkårsresultatRepository {
    private val vilkår = mutableMapOf<Long, VilkårsResultatEntity>()

    override fun lagreVilkårsResultat(
        vilkårsresultat: VilkårsResultatEntity,
        behandlingId: BehandlingId
    ): Long {
        vilkår[vilkår.size.toLong()] = vilkårsresultat
        return (vilkår.size - 1).toLong()
    }

    override fun hentVilkårsResultat(vilkårResultatId: Long): VilkårsResultatEntity? {
        TODO("Not yet implemented")
    }

    override fun hentForBehandling(behandlingsReferanse: UUID): VilkårsResultatEntity {
        TODO("Not yet implemented")
    }
}

class FakeBeregningsgrunnlagRepository : IBeregningsgrunnlagRepository {
    val grunnlag = mutableListOf<MedBehandlingsreferanse<IBeregningsGrunnlag>>()
    override fun lagreBeregningsGrunnlag(beregningsGrunnlag: MedBehandlingsreferanse<IBeregningsGrunnlag>): Long {
        grunnlag.add(beregningsGrunnlag)
        return grunnlag.indexOf(beregningsGrunnlag).toLong()
    }

    override fun hentBeregningsGrunnlag(referanse: UUID): List<MedBehandlingsreferanse<IBeregningsGrunnlag>> {
        return grunnlag
    }
}

class FakePdlGateway(val identerHemmelig: Map<String, Boolean> = emptyMap()) : PdlGateway {
    companion object : Factory<PdlGateway> {
        override fun konstruer(): PdlGateway {
            return FakePdlGateway()
        }
    }

    override fun hentPersoner(identer: List<String>): List<no.nav.aap.statistikk.integrasjoner.pdl.Person> {
        return identer.map {
            no.nav.aap.statistikk.integrasjoner.pdl.Person(
                adressebeskyttelse = listOf(Adressebeskyttelse(gradering = if (identerHemmelig[it] == true) Gradering.STRENGT_FORTROLIG else Gradering.UGRADERT))
            )
        }
    }
}

fun <E> ventPåSvar(getter: () -> E?, predicate: (E?) -> Boolean): E? {
    var res: E? = null
    val timeInMillis = measureTimeMillis {
        val maxTid = LocalDateTime.now().plusSeconds(10)
        var suksess = false
        while (maxTid.isAfter(LocalDateTime.now()) && !suksess) {
            try {
                res = getter()
                if (res != null && predicate(res)) {
                    suksess = true
                }
            } finally {
                Thread.sleep(50L)
            }
        }
    }
    logger.info("Ventet på at prosessering skulle fullføre, det tok $timeInMillis millisekunder. Res null: ${res == null}")
    if (res == null) {
        logger.info("Ventet på svar, men svaret er null.")
    }
    return res
}

fun forberedDatabase(
    it: DBConnection,
    behandlingReferanse: UUID
) {
    val ident = "214"
    val person = PersonService(PersonRepository(it)).hentEllerLagrePerson(ident)

    val sak = Sak(
        saksnummer = "ABCDE".tilSaksnummer(),
        person = person,
        sakStatus = SakStatus.LØPENDE,
        sistOppdatert = LocalDateTime.now()
    )
    val sakId = SakRepositoryImpl(it).settInnSak(sak)

    BehandlingRepository(it).opprettBehandling(
        Behandling(
            referanse = behandlingReferanse,
            sak = sak.copy(id = sakId),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = LocalDateTime.now(),
            mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
            versjon = Versjon("xxx"),
            søknadsformat = SøknadsFormat.DIGITAL,
        )
    )
}


fun konstruerSakstatistikkService(
    connection: DBConnection
): SaksStatistikkService {
    return SaksStatistikkService(
        behandlingRepository = BehandlingRepository(connection),
        rettighetstypeperiodeRepository = RettighetstypeperiodeRepository(connection),
        bigQueryKvitteringRepository = BigQueryKvitteringRepository(connection),
        skjermingService = SkjermingService(FakePdlGateway()),
        oppgaveHendelseRepository = OppgaveHendelseRepositoryImpl(connection),
        sakstatistikkRepository = SakstatistikkRepositoryImpl(connection),
    )
}


val schemaRegistry: SchemaRegistry = schemaRegistryYtelseStatistikk
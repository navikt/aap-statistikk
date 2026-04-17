package no.nav.aap.statistikk.testutils.builders

import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.ÅrsakTilOpprettelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.Vurderingsbehov
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.oppgave.OppgaveRepositoryImpl
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakId
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypeperiodeRepository
import no.nav.aap.statistikk.saksstatistikk.BQBehandlingMapper
import no.nav.aap.statistikk.saksstatistikk.SaksStatistikkService
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkEventSourcing
import no.nav.aap.statistikk.saksstatistikk.SakstatistikkRepositoryImpl
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

@JvmOverloads
fun opprettTestStoppetBehandling(
    behandlingReferanse: UUID,
    behandlingOpprettetTidspunkt: LocalDateTime,
    hendelsesTidspunkt: LocalDateTime,
    mottattTid: LocalDateTime,
    saksnummer: String = "123",
    behandlingStatus: Status = Status.OPPRETTET,
    behandlingType: TypeBehandling = TypeBehandling.Førstegangsbehandling,
    ident: String = "0",
    avklaringsbehov: List<AvklaringsbehovHendelseDto> = listOf()
): StoppetBehandling =
    StoppetBehandling(
        saksnummer = saksnummer,
        behandlingStatus = behandlingStatus,
        behandlingType = behandlingType,
        ident = ident,
        behandlingReferanse = behandlingReferanse,
        behandlingOpprettetTidspunkt = behandlingOpprettetTidspunkt,
        avklaringsbehov = avklaringsbehov,
        versjon = "UKJENT",
        mottattTid = mottattTid,
        sakStatus = no.nav.aap.behandlingsflyt.kontrakt.sak.Status.UTREDES,
        hendelsesTidspunkt = hendelsesTidspunkt,
        vurderingsbehov = listOf(no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov.SØKNAD),
        årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD
    )

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
        typeBehandling = no.nav.aap.statistikk.behandling.TypeBehandling.Førstegangsbehandling,
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

fun forberedDatabase(
    it: DBConnection,
    behandlingReferanse: UUID
): BehandlingId {
    val ident = "214"
    val person = PersonService(PersonRepository(it)).hentEllerLagrePerson(ident)

    val sak = Sak(
        saksnummer = "ABCDE".tilSaksnummer(),
        person = person,
        sakStatus = SakStatus.LØPENDE,
        sistOppdatert = LocalDateTime.now()
    )
    val sakId = SakRepositoryImpl(it).settInnSak(sak)

    return BehandlingRepository(it).opprettBehandling(
        Behandling(
            referanse = behandlingReferanse,
            sak = sak.copy(id = sakId),
            typeBehandling = no.nav.aap.statistikk.behandling.TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = LocalDateTime.now(),
            mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
            versjon = Versjon("xxx"),
            søknadsformat = SøknadsFormat.DIGITAL,
            oppdatertTidspunkt = LocalDateTime.now()
        )
    )
}

fun konstruerSakstatistikkService(
    connection: DBConnection
): SaksStatistikkService {
    val behandlingService = BehandlingService(
        BehandlingRepository(connection),
        SkjermingService(FakePdlGateway())
    )
    return SaksStatistikkService(
        behandlingService = behandlingService,
        sakstatistikkRepository = SakstatistikkRepositoryImpl(connection),
        bqBehandlingMapper = BQBehandlingMapper(
            behandlingService,
            RettighetstypeperiodeRepository(connection),
            OppgaveRepositoryImpl(connection),
            SakstatistikkEventSourcing()
        )
    )
}

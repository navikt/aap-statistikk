package no.nav.aap.statistikk.testutils.fakes

import no.nav.aap.komponenter.gateway.Factory
import no.nav.aap.komponenter.type.Periode
import no.nav.aap.statistikk.avsluttetbehandling.ArbeidsopptrappingperioderRepository
import no.nav.aap.statistikk.avsluttetbehandling.IRettighetstypeperiodeRepository
import no.nav.aap.statistikk.avsluttetbehandling.RettighetstypePeriode
import no.nav.aap.statistikk.avsluttetbehandling.StansEllerOpphør
import no.nav.aap.statistikk.avsluttetbehandling.VedtattStansOpphørRepository
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.DiagnoseEntity
import no.nav.aap.statistikk.behandling.DiagnoseRepository
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.bigquery.BQTable
import no.nav.aap.statistikk.bigquery.IBigQueryClient
import no.nav.aap.statistikk.bigquery.IBQYtelsesstatistikkRepository
import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.integrasjoner.pdl.Adressebeskyttelse
import no.nav.aap.statistikk.integrasjoner.pdl.Gradering
import no.nav.aap.statistikk.integrasjoner.pdl.PdlGateway
import no.nav.aap.statistikk.meldekort.FritaksvurderingRepository
import no.nav.aap.statistikk.meldekort.Fritakvurdering
import no.nav.aap.statistikk.meldekort.IMeldekortRepository
import no.nav.aap.statistikk.meldekort.Meldekort
import no.nav.aap.statistikk.oppgave.EnhetReservasjonOgTidspunkt
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepository
import no.nav.aap.statistikk.person.IPersonRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakId
import no.nav.aap.statistikk.sak.SakRepository
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

private val logger = LoggerFactory.getLogger("FakeRepositories")

object FakeBigQueryClient : IBigQueryClient {
    override fun <E> create(table: BQTable<E>): Boolean {
        return true
    }

    override fun <E> insert(table: BQTable<E>, value: E) = Unit

    override fun <E> read(table: BQTable<E>): List<E> {
        return emptyList()
    }

    override fun <E> read(
        table: BQTable<E>,
        whereClause: String
    ): List<E> {
        return emptyList()
    }

    override fun <E> insertMany(
        table: BQTable<E>,
        values: List<E>
    ) = Unit
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
                relatertBehandlingReferanse = behandling.relatertBehandlingReferanse
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
                søknadsformat = behandling.søknadsformat,
                relatertBehandlingReferanse = behandling.relatertBehandlingReferanse
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

class FakeArbeidsopptrappingRepository : ArbeidsopptrappingperioderRepository {
    override fun lagre(
        behandlingId: BehandlingId,
        perioder: List<Periode>
    ) {
        TODO("Not yet implemented")
    }

    override fun hent(behandlingId: BehandlingId): List<Periode>? {
        TODO("Not yet implemented")
    }
}

class FakeVedtattStansOpphørRepository : VedtattStansOpphørRepository {
    private val lagret = mutableMapOf<BehandlingId, List<StansEllerOpphør>>()

    override fun lagre(behandlingId: BehandlingId, vedtattStansOpphør: List<StansEllerOpphør>) {
        lagret[behandlingId] = vedtattStansOpphør
    }

    override fun hent(behandlingId: BehandlingId): List<StansEllerOpphør> =
        lagret[behandlingId] ?: emptyList()
}

class FakeFritaksvurderingRepository : FritaksvurderingRepository {
    override fun lagre(behandlingId: BehandlingId, vurderinger: List<Fritakvurdering>) {
        TODO("Not yet implemented")
    }

    override fun hentFritaksvurderinger(behandlingId: BehandlingId): List<Fritakvurdering> {
        TODO("Not yet implemented")
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

    override fun commit() = Unit

    override fun start() = Unit
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

class FakeOppgaveHendelseRepository : OppgaveHendelseRepository {
    private val enhetReservasjoner =
        mutableMapOf<Pair<UUID, String>, List<EnhetReservasjonOgTidspunkt>>()

    fun addEnhetReservasjon(
        behandlingRef: UUID,
        avklaringsbehovKode: String,
        data: List<EnhetReservasjonOgTidspunkt>
    ) {
        enhetReservasjoner[behandlingRef to avklaringsbehovKode] = data
    }

    override fun hentEnhetOgReservasjonForAvklaringsbehov(
        behandlingReferanse: UUID,
        avklaringsbehovKode: String
    ): List<EnhetReservasjonOgTidspunkt> {
        return enhetReservasjoner[behandlingReferanse to avklaringsbehovKode] ?: emptyList()
    }

    override fun lagreHendelse(hendelse: OppgaveHendelse) = 0L
    override fun sisteVersjonForId(id: Long) = null
    override fun hentHendelserForId(id: Long) =
        emptyList<OppgaveHendelse>()
}

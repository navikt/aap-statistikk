package no.nav.aap.statistikk.person

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Collections.synchronizedList
import java.util.concurrent.CountDownLatch
import javax.sql.DataSource

class PersonRepositoryTest {
    @Test
    fun `sett inn og hent ut person`(@Postgres dataSource: DataSource) {
        val ident = Ident("13021913")
        dataSource.transaction {
            PersonRepository(it).lagrePerson(
                Person(
                    ident = ident,
                ),
                setOf(ident),
            )
        }

        val uthentet = dataSource.transaction { PersonRepository(it).hentPerson(ident = ident) }

        assertThat(uthentet?.ident).isEqualTo(
            ident
        )
        assertThat(uthentet?.id()).isNotNull()
    }

    @Test
    fun `lagrer og henter ut om person er skjermet`(@Postgres dataSource: DataSource) {
        val ident = Ident("13021914")
        dataSource.transaction {
            PersonRepository(it).lagrePerson(Person(ident = ident, skjermet = true), setOf(ident))
        }

        val uthentet = dataSource.transaction { PersonRepository(it).hentPerson(ident = ident) }

        assertThat(uthentet?.erSkjermet()).isTrue()
    }

    @Test
    fun `oppdaterer skjermet ved lagring av eksisterende person`(@Postgres dataSource: DataSource) {
        val ident = Ident("13021921")
        val personId = dataSource.transaction {
            PersonRepository(it).lagrePerson(Person(ident = ident), setOf(ident))
        }

        dataSource.transaction {
            PersonRepository(it).lagrePerson(
                Person(ident = ident, skjermet = true, id = personId),
                setOf(ident),
            )
        }

        val oppdatert = dataSource.transaction { PersonRepository(it).hentPerson(ident) }
        assertThat(oppdatert?.erSkjermet()).isTrue()
    }

    @Test
    fun `lagrer alle historiske identer ved opprettelse`(@Postgres dataSource: DataSource) {
        val historiskIdent = Ident("13021919")
        val aktivIdent = Ident("13021920")

        val personId = dataSource.transaction {
            PersonRepository(it).lagrePerson(
                Person(ident = aktivIdent),
                setOf(historiskIdent, aktivIdent),
            )
        }

        val hentetPåHistoriskIdent =
            dataSource.transaction { PersonRepository(it).hentPerson(historiskIdent) }

        assertThat(hentetPåHistoriskIdent?.id()).isEqualTo(personId)
        assertThat(hentetPåHistoriskIdent?.ident).isEqualTo(aktivIdent)
    }

    @Test
    fun `kan hente person på gammel ident etter identbytte, kun én ident er aktiv`(@Postgres dataSource: DataSource) {
        val gammelIdent = Ident("13021915")
        val nyIdent = Ident("13021916")

        val personId = dataSource.transaction {
            PersonRepository(it).lagrePerson(Person(ident = gammelIdent), setOf(gammelIdent))
        }

        dataSource.transaction {
            val eksisterende = requireNotNull(PersonRepository(it).hentPerson(gammelIdent))
            PersonRepository(it).lagrePerson(
                Person(ident = nyIdent, id = eksisterende.id()),
                setOf(gammelIdent, nyIdent),
            )
        }

        val uthentetPåGammelIdent =
            dataSource.transaction { PersonRepository(it).hentPerson(ident = gammelIdent) }
        val uthentetPåNyIdent =
            dataSource.transaction { PersonRepository(it).hentPerson(ident = nyIdent) }

        assertThat(uthentetPåGammelIdent?.id()).isEqualTo(personId)
        assertThat(uthentetPåNyIdent?.id()).isEqualTo(personId)
        // Begge oppslagene skal returnere personen med den aktive (nye) identen.
        assertThat(uthentetPåGammelIdent?.ident).isEqualTo(nyIdent)
        assertThat(uthentetPåNyIdent?.ident).isEqualTo(nyIdent)
    }

    @Test
    fun `lagrePerson kaster hvis ny ident allerede tilhører en annen person`(@Postgres dataSource: DataSource) {
        val ident1 = Ident("13021917")
        val ident2 = Ident("13021918")

        val person1Id =
            dataSource.transaction { PersonRepository(it).lagrePerson(Person(ident = ident1), setOf(ident1)) }
        dataSource.transaction { PersonRepository(it).lagrePerson(Person(ident = ident2), setOf(ident2)) }

        assertThrows<IllegalArgumentException> {
            dataSource.transaction {
                PersonRepository(it).lagrePerson(Person(ident = ident2, id = person1Id), setOf(ident2))
            }
        }
    }

    @Test
    fun `slår sammen eksisterende personrader når PDL knytter identene sammen`(@Postgres dataSource: DataSource) {
        val historiskIdent = Ident("13021924")
        val aktivIdent = Ident("13021925")
        dataSource.transaction {
            PersonRepository(it).lagrePerson(Person(ident = historiskIdent), setOf(historiskIdent))
        }
        val aktivPersonId = dataSource.transaction {
            PersonRepository(it).lagrePerson(Person(ident = aktivIdent), setOf(aktivIdent))
        }

        val sammenslåttPersonId = dataSource.transaction {
            val repository = PersonRepository(it)
            repository.slåSammenPersoner(
                beholdPersonId = aktivPersonId,
                fjernPersonIder = setOf(requireNotNull(repository.hentPerson(historiskIdent)?.id())),
                identer = setOf(historiskIdent, aktivIdent),
            )
            repository.lagrePerson(
                Person(ident = aktivIdent, id = aktivPersonId),
                setOf(historiskIdent, aktivIdent),
            )
        }

        val hentetPåHistoriskIdent =
            dataSource.transaction { PersonRepository(it).hentPerson(historiskIdent) }
        assertThat(sammenslåttPersonId).isEqualTo(aktivPersonId)
        assertThat(hentetPåHistoriskIdent?.id()).isEqualTo(aktivPersonId)
        assertThat(hentetPåHistoriskIdent?.ident).isEqualTo(aktivIdent)
    }

    @Test
    fun `samtidig opprettelse av samme person returnerer samme person-id`(@Postgres dataSource: DataSource) {
        val ident = Ident("13021923")
        val klar = CountDownLatch(2)
        val start = CountDownLatch(1)
        val personIder = synchronizedList(mutableListOf<Long>())
        val feil = synchronizedList(mutableListOf<Throwable>())

        val tråder = List(2) {
            Thread {
                runCatching {
                    klar.countDown()
                    start.await()
                    val personId = dataSource.transaction {
                        PersonRepository(it).lagrePerson(Person(ident = ident), setOf(ident))
                    }
                    personIder.add(personId)
                }.onFailure(feil::add)
            }
        }

        tråder.forEach(Thread::start)
        klar.await()
        start.countDown()
        tråder.forEach { it.join(5_000) }

        assertThat(feil).isEmpty()
        assertThat(personIder).hasSize(2).allMatch { it == personIder.first() }
    }

}
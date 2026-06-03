package no.nav.aap.statistikk.behandling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.opprettTestPerson
import no.nav.aap.statistikk.testutils.builders.opprettTestSak
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * Tests for pessimistic locking on behandling_id.
 * 
 * These tests verify that:
 * 1. Lock can be acquired and released properly
 * 2. Concurrent access to same behandling is serialized
 * 3. Different behandlinger can be accessed concurrently
 * 4. Lock respects transaction boundaries
 */
class BehandlingPessimisticLockTest {

    @Test
    fun `hentBehandlingForUpdate should acquire lock on behandling`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)
        val referanse = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("v1"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        val locked = dataSource.transaction {
            BehandlingRepository(it).hentBehandlingForUpdate(behandlingId)
        }

        assertThat(locked).isNotNull()
        assertThat(locked!!.id()).isEqualTo(behandlingId)
        assertThat(locked.referanse).isEqualTo(referanse)
    }

    @Test
    fun `hentBehandlingForUpdate by referanse should acquire lock`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)
        val referanse = UUID.randomUUID()

        dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("v1"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        val locked = dataSource.transaction {
            BehandlingRepository(it).hentBehandlingForUpdate(referanse)
        }

        assertThat(locked).isNotNull()
        assertThat(locked!!.referanse).isEqualTo(referanse)
    }

    @Test
    fun `hentBehandlingForUpdate by referanse returns null if not found`(@Postgres dataSource: DataSource) {
        val nonExistentReferanse = UUID.randomUUID()

        val result = dataSource.transaction {
            BehandlingRepository(it).hentBehandlingForUpdate(nonExistentReferanse)
        }

        assertThat(result).isNull()
    }

    @Test
    fun `concurrent writes to same behandling are serialized`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)
        val referanse = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("v1"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        // Track execution order
        val executionOrder = mutableListOf<String>()
        val lockAcquiredCount = AtomicInteger(0)

        runBlocking {
            val ready = CountDownLatch(2)

            val task1 = async(Dispatchers.IO) {
                ready.countDown()
                ready.await()
                
                dataSource.transaction {
                    val locked = BehandlingRepository(it).hentBehandlingForUpdate(behandlingId)
                    lockAcquiredCount.incrementAndGet()
                    executionOrder.add("task1-locked")

                    // Hold lock for a bit
                    Thread.sleep(500)
                    
                    BehandlingRepository(it).oppdaterBehandling(
                        locked.copy(
                            sisteSaksbehandler = "handler1",
                            oppdatertTidspunkt = LocalDateTime.now()
                        )
                    )
                    executionOrder.add("task1-updated")
                }
            }

            val task2 = async(Dispatchers.IO) {
                ready.countDown()
                ready.await()
                
                // Give task1 time to acquire lock
                Thread.sleep(100)

                dataSource.transaction {
                    val locked = BehandlingRepository(it).hentBehandlingForUpdate(behandlingId)
                    lockAcquiredCount.incrementAndGet()
                    executionOrder.add("task2-locked")

                    BehandlingRepository(it).oppdaterBehandling(
                        locked.copy(
                            sisteSaksbehandler = "handler2",
                            oppdatertTidspunkt = LocalDateTime.now()
                        )
                    )
                    executionOrder.add("task2-updated")
                }
            }

            task1.await()
            task2.await()
        }

        // Both should successfully acquire lock
        assertThat(lockAcquiredCount.get()).isEqualTo(2)
        
        // task1 should complete before task2 can lock
        assertThat(executionOrder).startsWith("task1-locked")
        assertThat(executionOrder.indexOf("task1-updated"))
            .isLessThan(executionOrder.indexOf("task2-locked"))
    }

    @Test
    fun `different behandlinger can be locked concurrently`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)

        val behandling1Id = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = UUID.randomUUID(),
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("v1"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        val behandling2Id = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = UUID.randomUUID(),
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("v1"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        val results = ConcurrentHashMap<String, String>()
        val taskStartTime = System.currentTimeMillis()
        val taskDuration = 500L

        runBlocking {
            val task1 = async(Dispatchers.IO) {
                dataSource.transaction {
                    val locked = BehandlingRepository(it).hentBehandlingForUpdate(behandling1Id)
                    results["task1-lock"] = "locked"
                    
                    // Hold lock for 500ms
                    Thread.sleep(taskDuration)
                    
                    results["task1-release"] = "released"
                }
            }

            val task2 = async(Dispatchers.IO) {
                dataSource.transaction {
                    val locked = BehandlingRepository(it).hentBehandlingForUpdate(behandling2Id)
                    results["task2-lock"] = "locked"
                    
                    // Hold lock for 500ms
                    Thread.sleep(taskDuration)
                    
                    results["task2-release"] = "released"
                }
            }

            task1.await()
            task2.await()
        }

        val elapsedMs = System.currentTimeMillis() - taskStartTime

        // Both tasks should complete
        assertThat(results).containsKeys("task1-lock", "task1-release", "task2-lock", "task2-release")
        
        // Since they're different behandlinger, they should run concurrently
        // If run sequentially, would take 1000ms. If concurrent, should take ~500ms.
        assertThat(elapsedMs).isLessThan(800)  // Allow 300ms buffer for overhead
    }

    @Test
    @Timeout(10)  // 10 second timeout - should complete much faster
    fun `lock is released at transaction end`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)
        val referanse = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("v1"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        val acquiredLocks = AtomicInteger(0)

        runBlocking {
            // First transaction: lock and immediately release
            dataSource.transaction {
                BehandlingRepository(it).hentBehandlingForUpdate(behandlingId)
                acquiredLocks.incrementAndGet()
            }
            // Lock released here at transaction end

            // Second transaction: should acquire lock without waiting
            val startTime = System.currentTimeMillis()
            dataSource.transaction {
                BehandlingRepository(it).hentBehandlingForUpdate(behandlingId)
                acquiredLocks.incrementAndGet()
            }
            val elapsedMs = System.currentTimeMillis() - startTime

            // Should acquire lock almost immediately (no queue)
            assertThat(elapsedMs).isLessThan(1000)
            assertThat(acquiredLocks.get()).isEqualTo(2)
        }
    }

    @Test
    fun `duplicate event is handled idempotently with lock`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)
        val referanse = UUID.randomUUID()

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(
                Behandling(
                    referanse = referanse,
                    sak = sak,
                    typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon("v1"),
                    søknadsformat = SøknadsFormat.PAPIR,
                    relaterteIdenter = listOf(),
                    oppdatertTidspunkt = LocalDateTime.now(),
                )
            )
        }

        // Simulate two pods processing same event
        runBlocking {
            val ready = CountDownLatch(2)

            val task1 = async(Dispatchers.IO) {
                ready.countDown()
                ready.await()
                
                dataSource.transaction {
                    val locked = BehandlingRepository(it).hentBehandlingForUpdate(referanse)
                    assertThat(locked).isNotNull()
                    
                    // Update saksbehandler
                    BehandlingRepository(it).oppdaterBehandling(
                        locked!!.copy(
                            sisteSaksbehandler = "s001",
                            oppdatertTidspunkt = LocalDateTime.now()
                        )
                    )
                }
            }

            val task2 = async(Dispatchers.IO) {
                ready.countDown()
                ready.await()
                
                // Small delay to ensure task1 processes first
                Thread.sleep(50)
                
                dataSource.transaction {
                    val locked = BehandlingRepository(it).hentBehandlingForUpdate(referanse)
                    assertThat(locked).isNotNull()
                    
                    // Same saksbehandler update (idempotent)
                    BehandlingRepository(it).oppdaterBehandling(
                        locked!!.copy(
                            sisteSaksbehandler = "s001",
                            oppdatertTidspunkt = LocalDateTime.now()
                        )
                    )
                }
            }

            task1.await()
            task2.await()
        }

        // Verify only ONE behandling row exists
        val final = dataSource.transaction {
            BehandlingRepository(it).hent(referanse)
        }
        assertThat(final).isNotNull()
        assertThat(final!!.id()).isEqualTo(behandlingId)
        
        // Verify two historikk entries (one from each event)
        assertThat(final.hendelser.size).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `update with locked behandling is consistent`(@Postgres dataSource: DataSource) {
        val person = opprettTestPerson(dataSource, "123456789")
        val sak = opprettTestSak(dataSource, "123456789".tilSaksnummer(), person)
        val referanse = UUID.randomUUID()

        val initialBehandling = Behandling(
            referanse = referanse,
            sak = sak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
                    status = BehandlingStatus.UTREDES,
            opprettetTid = LocalDateTime.now(),
            mottattTid = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS),
            versjon = Versjon("v1"),
            søknadsformat = SøknadsFormat.PAPIR,
            sisteSaksbehandler = "s001",
            relaterteIdenter = listOf(),
            oppdatertTidspunkt = LocalDateTime.now(),
        )

        val behandlingId = dataSource.transaction {
            BehandlingRepository(it).opprettBehandling(initialBehandling)
        }

        // Lock, modify, and update
        val updated = dataSource.transaction {
            val locked = BehandlingRepository(it).hentBehandlingForUpdate(behandlingId)
            val modified = locked.copy(
                sisteSaksbehandler = "s002",
                oppdatertTidspunkt = LocalDateTime.now()
            )
            BehandlingRepository(it).oppdaterBehandling(modified)
            modified
        }

        // Verify update was persisted
        val fetched = dataSource.transaction {
            BehandlingRepository(it).hent(referanse)
        }

        assertThat(fetched!!.sisteSaksbehandler).isEqualTo("s002")
    }
}

package no.nav.aap.statistikk.testutils.fakes

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.BehandlingTilstand
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class FakeBehandlingRepositoryTest {
    @Test
    fun `lesing uten historikk beholder tilstanden uten å fjerne lagrede hendelser`() {
        val tidspunkt = LocalDateTime.of(2026, 11, 19, 10, 0)
        val repository = FakeBehandlingRepository()
        val id = repository.opprettBehandling(
            Behandling(
                referanse = UUID.randomUUID(),
                sak = Sak(
                    saksnummer = Saksnummer("SAK123"),
                    person = Person(ident = Ident("0")),
                    sistOppdatert = tidspunkt,
                ),
                typeBehandling = TypeBehandling.Førstegangsbehandling,
                opprettetTid = tidspunkt,
                tilstand = BehandlingTilstand(
                    status = BehandlingStatus.UTREDES,
                    versjon = Versjon("1"),
                    mottattTid = tidspunkt,
                    søknadsformat = SøknadsFormat.DIGITAL,
                ),
            ),
        )
        val medHistorikk = repository.hentMedHistorikk(id)

        val utenHistorikk = repository.hentUtenHistorikkEllerNull(id)

        assertThat(medHistorikk.hendelser).hasSize(1)
        assertThat(utenHistorikk).isEqualTo(medHistorikk.copy(hendelser = emptyList()))
        assertThat(repository.hentMedHistorikk(id)).isEqualTo(medHistorikk)
    }
}

package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class ReberegnHistorikkTest {
    @Test
    fun `begynne på test på reberegne historikk`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")

        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = hendelse.ident),
                sakStatus = hendelse.sakStatus.tilDomene(),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            status = hendelse.behandlingStatus.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            mottattTid = hendelse.mottattTid,
            vedtakstidspunkt = null,
            ansvarligBeslutter = null,
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.DIGITAL,
            sisteSaksbehandler = null,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            gjeldendeAvklaringsBehov = null,
            gjeldendeAvklaringsbehovStatus = null,
            venteÅrsak = null,
            returÅrsak = null,
            gjeldendeStegGruppe = null,
            årsaker = listOf(),
            resultat = null,
            oppdatertTidspunkt = null,
            hendelser = listOf()
        )

        val res = ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        assertThat(res.status).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.gjeldendeAvklaringsBehov).isNull()
    }

    @Test
    fun `reberegne historikk for tom behandling`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/resendt_revurdering_automatisk.json")

        val behandling = Behandling(
            referanse = hendelse.behandlingReferanse,
            sak = Sak(
                saksnummer = hendelse.saksnummer.let(::Saksnummer),
                person = Person(ident = hendelse.ident),
                sakStatus = hendelse.sakStatus.tilDomene(),
                sistOppdatert = hendelse.hendelsesTidspunkt,
            ),
            typeBehandling = hendelse.behandlingType.tilDomene(),
            status = hendelse.behandlingStatus.tilDomene(),
            opprettetTid = hendelse.behandlingOpprettetTidspunkt,
            mottattTid = hendelse.mottattTid,
            vedtakstidspunkt = null,
            ansvarligBeslutter = null,
            versjon = Versjon(UUID.randomUUID().toString()),
            søknadsformat = SøknadsFormat.DIGITAL,
            sisteSaksbehandler = null,
            relaterteIdenter = listOf(),
            relatertBehandlingId = null,
            gjeldendeAvklaringsBehov = null,
            gjeldendeAvklaringsbehovStatus = null,
            venteÅrsak = null,
            returÅrsak = null,
            gjeldendeStegGruppe = null,
            årsaker = listOf(),
            resultat = null,
            oppdatertTidspunkt = null,
            hendelser = listOf()
        )

        val res = ReberegnHistorikk().avklaringsbehovTilHistorikk(hendelse, behandling)

        assertThat(res.status).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(res.hendelser).hasSize(1)

        assertThat(res.hendelser.last().status).isEqualTo(BehandlingStatus.AVSLUTTET)
    }
}
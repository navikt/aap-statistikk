package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.oppgave.Saksbehandler
import java.time.LocalDateTime

class ReberegnHistorikk {
    fun avklaringsbehovTilHistorikk(
        dto: StoppetBehandling, behandling: Behandling
    ): Behandling {
        val avklaringsbehov = dto.avklaringsbehov

        val avklaringsbehovEndringer = avklaringsbehov
            .flatMap { behov -> behov.endringer.map { Pair(behov, it) } }
            .sortedBy { it.second.tidsstempel }

        val endringsTidspunkter = avklaringsbehov.flatMap {
            it.endringer.map { it.tidsstempel }
        }

        val avklaringsbehovHistorikk = endringsTidspunkter.map { tidspunkt ->
            avklaringsbehov.påTidspunkt(tidspunkt)
        }.filterNot {
            // Fjerne "ugyldig" tilstand
            it.utledAnsvarligBeslutter() == null && it.sisteAvklaringsbehovStatus() == null
        }
        return avklaringsbehovHistorikk.fold(behandling) { acc, curr ->
            acc.leggTilHendelse(
                BehandlingHendelse(
                    tidspunkt = null, // Vil etterfylles
                    hendelsesTidspunkt = curr.map { it.tidspunktSisteEndring() }.max(),
                    avklaringsBehov = curr.utledGjeldendeAvklaringsBehov()?.kode?.name,
                    avklaringsbehovStatus = curr.sisteAvklaringsbehovStatus(),
                    steggruppe = curr.utledGjeldendeStegType()?.gruppe,
                    venteÅrsak = curr.utledÅrsakTilSattPåVent(),
                    returÅrsak = curr.årsakTilRetur()?.name,
                    saksbehandler = curr.sistePersonPåBehandling()?.let(::Saksbehandler),
                    resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
                    versjon = dto.versjon.let(::Versjon),
                    status = curr.utledBehandlingStatus(),
                    ansvarligBeslutter = curr.utledAnsvarligBeslutter(),
                    vedtakstidspunkt = curr.utledVedtakTid(),
                    mottattTid = dto.mottattTid,
                    søknadsformat = dto.soknadsFormat.tilDomene(),
                )
            )
        }
    }
}
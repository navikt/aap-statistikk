package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.oppgave.Saksbehandler

class ReberegnHistorikk {
    fun avklaringsbehovTilHistorikk(
        dto: StoppetBehandling, behandling: Behandling
    ): Behandling {
        val avklaringsbehov = dto.avklaringsbehov

        if (avklaringsbehov.isEmpty() && dto.behandlingStatus.tilDomene() == BehandlingStatus.AVSLUTTET) {
            return behandling
                .leggTilHendelse(
                    BehandlingHendelse(
                        tidspunkt = dto.tidspunktSisteEndring,
                        hendelsesTidspunkt = dto.hendelsesTidspunkt,
                        avklaringsBehov = null,
                        steggruppe = null,
                        avklaringsbehovStatus = null,
                        venteÅrsak = null,
                        returÅrsak = null,
                        saksbehandler = null,
                        resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
                        versjon = dto.versjon.let(::Versjon),
                        status = dto.behandlingStatus.tilDomene(),
                        ansvarligBeslutter = null,
                        vedtakstidspunkt = dto.tidspunktSisteEndring,
                        mottattTid = dto.mottattTid,
                        søknadsformat = dto.soknadsFormat.tilDomene(),
                    )
                )
        }

        val endringsTidspunkter = avklaringsbehov.flatMap {
            it.endringer.map { endring -> endring.tidsstempel }
        }.sortedBy { it }

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
                    hendelsesTidspunkt = requireNotNull(curr.tidspunktSisteEndring()),
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
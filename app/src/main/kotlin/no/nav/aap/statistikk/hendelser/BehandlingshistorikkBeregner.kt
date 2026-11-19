package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.BehandlingTilstand
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.behandling.tilAvklaringsbehovKode
import no.nav.aap.statistikk.behandling.tilDomene
import no.nav.aap.statistikk.behandling.tilTilstand

class BehandlingshistorikkBeregner {
    fun beregn(dto: StoppetBehandling, behandling: Behandling): Behandling {
        val avklaringsbehov = dto.avklaringsbehov
        val sisteLøsteBehov =
            avklaringsbehov.utledForrigeLøsteAvklaringsbehov()?.let { (definisjon, løstAv, tidspunkt) ->
                LøstBehov(definisjon, løstAv, tidspunkt)
            }
        val inngangshendelse = inngangshendelse(dto, sisteLøsteBehov)

        if (avklaringsbehov.isEmpty() && dto.behandlingStatus.tilDomene() == BehandlingStatus.AVSLUTTET) {
            return behandling
                .leggTilHendelse(inngangshendelse)
                .leggTilHendelse(avslutningshendelseUtenAvklaringsbehov(dto, sisteLøsteBehov))
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

        val finnesInngangshendelse =
            behandling.hendelsesHistorikk()
                .any { it.behandlingStatus() == BehandlingStatus.OPPRETTET }

        val behandlingMedEllerUtenInngangshendelse =
            if (finnesInngangshendelse) {
                behandling
            } else {
                behandling.copy(
                    hendelser = listOf(inngangshendelse) + behandling.hendelser,
                )
            }

        return avklaringsbehovHistorikk.fold(behandlingMedEllerUtenInngangshendelse) { acc, curr ->
            acc.leggTilHendelse(
                hendelseFraAvklaringsbehov(dto, curr, behandlingMedEllerUtenInngangshendelse),
            )
        }.let {
            if (it.behandlingStatus() != BehandlingStatus.AVSLUTTET &&
                dto.behandlingStatus.tilDomene() == BehandlingStatus.AVSLUTTET
            ) {
                it.leggTilHendelse(
                    avslutningshendelseEtterHistorikk(dto, behandlingMedEllerUtenInngangshendelse, sisteLøsteBehov),
                )
            } else {
                it
            }
        }
    }

    private fun inngangshendelse(dto: StoppetBehandling, sisteLøsteBehov: LøstBehov?): BehandlingHendelse =
        BehandlingHendelse(
            tidspunkt = dto.mottattTid,
            hendelsesTidspunkt = dto.mottattTid,
            tilstand = BehandlingTilstand(
                resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
                versjon = dto.versjon.let(::Versjon),
                status = BehandlingStatus.OPPRETTET,
                mottattTid = dto.mottattTid,
                søknadsformat = dto.soknadsFormat.tilDomene(),
                relatertBehandlingReferanse = dto.relatertBehandling?.toString(),
                sisteLøsteAvklaringsbehov = sisteLøsteBehov?.definisjon?.tilAvklaringsbehovKode(),
                sisteSaksbehandlerSomLøstebehov = sisteLøsteBehov?.løstAv,
                sistLøsteAvklaringsbehovTidspunkt = sisteLøsteBehov?.tidspunkt,
            ),
        )

    private fun avslutningshendelseUtenAvklaringsbehov(
        dto: StoppetBehandling,
        sisteLøsteBehov: LøstBehov?,
    ): BehandlingHendelse = BehandlingHendelse(
        tidspunkt = dto.tidspunktSisteEndring,
        hendelsesTidspunkt = dto.avsluttetBehandling?.vedtakstidspunkt
            ?: dto.tidspunktSisteEndring ?: dto.behandlingOpprettetTidspunkt,
        tilstand = BehandlingTilstand(
            resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
            versjon = dto.versjon.let(::Versjon),
            status = dto.behandlingStatus.tilDomene(),
            vedtakstidspunkt = dto.avsluttetBehandling?.vedtakstidspunkt
                ?: dto.tidspunktSisteEndring,
            mottattTid = dto.mottattTid,
            søknadsformat = dto.soknadsFormat.tilDomene(),
            relatertBehandlingReferanse = dto.relatertBehandling?.toString(),
            sisteLøsteAvklaringsbehov = sisteLøsteBehov?.definisjon?.tilAvklaringsbehovKode(),
            sisteSaksbehandlerSomLøstebehov = sisteLøsteBehov?.løstAv,
            sistLøsteAvklaringsbehovTidspunkt = sisteLøsteBehov?.tidspunkt,
        ),
    )

    private fun hendelseFraAvklaringsbehov(
        dto: StoppetBehandling,
        avklaringsbehov: List<AvklaringsbehovHendelseDto>,
        behandlingVedStart: Behandling,
    ): BehandlingHendelse {
        val behovstilstand = avklaringsbehov.utledAvklaringsbehovTilstand()
        return BehandlingHendelse(
            tidspunkt = null, // Vil etterfylles
            hendelsesTidspunkt = requireNotNull(avklaringsbehov.tidspunktSisteEndring()),
            tilstand = BehandlingTilstand(
                gjeldendeAvklaringsBehov = behovstilstand.gjeldendeBehov?.definisjon?.tilAvklaringsbehovKode(),
                gjeldendeAvklaringsbehovStatus = behovstilstand.gjeldendeBehov?.status?.tilDomene(),
                gjeldendeStegGruppe = behovstilstand.gjeldendeBehov?.steggruppe?.tilDomene(),
                venteÅrsak = behovstilstand.venteÅrsak,
                returÅrsak = behovstilstand.returÅrsak,
                returÅrsakkoblinger = behovstilstand.returÅrsakkoblinger.map { it.tilTilstand() },
                sisteSaksbehandler = behovstilstand.saksbehandler,
                resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
                versjon = dto.versjon.let(::Versjon),
                status = avklaringsbehov.utledBehandlingStatus(),
                ansvarligBeslutter = behovstilstand.ansvarligBeslutter,
                vedtakstidspunkt = if (behandlingVedStart.behandlingStatus() == BehandlingStatus.AVSLUTTET) {
                    behandlingVedStart.vedtakstidspunkt ?: avklaringsbehov.utledVedtakTid()
                } else {
                    null
                },
                mottattTid = dto.mottattTid,
                søknadsformat = dto.soknadsFormat.tilDomene(),
                relatertBehandlingReferanse = dto.relatertBehandling?.toString(),
                sisteLøsteAvklaringsbehov = behovstilstand.sisteLøsteBehov?.definisjon?.tilAvklaringsbehovKode(),
                sisteSaksbehandlerSomLøstebehov = behovstilstand.sisteLøsteBehov?.løstAv,
                sistLøsteAvklaringsbehovTidspunkt = behovstilstand.sisteLøsteBehov?.tidspunkt,
            ),
        )
    }

    private fun avslutningshendelseEtterHistorikk(
        dto: StoppetBehandling,
        behandlingVedStart: Behandling,
        sisteLøsteBehov: LøstBehov?,
    ): BehandlingHendelse = BehandlingHendelse(
        tidspunkt = null, // Vil etterfylles
        hendelsesTidspunkt = requireNotNull(
            dto.avsluttetBehandling?.vedtakstidspunkt ?: dto.tidspunktSisteEndring,
        ),
        tilstand = BehandlingTilstand(
            resultat = dto.avsluttetBehandling?.resultat.resultatTilDomene(),
            versjon = dto.versjon.let(::Versjon),
            status = BehandlingStatus.AVSLUTTET,
            ansvarligBeslutter = behandlingVedStart.hendelsesHistorikk().lastOrNull()?.ansvarligBeslutter,
            vedtakstidspunkt = behandlingVedStart.vedtakstidspunkt,
            mottattTid = dto.mottattTid,
            søknadsformat = dto.soknadsFormat.tilDomene(),
            relatertBehandlingReferanse = dto.relatertBehandling?.toString(),
            sisteLøsteAvklaringsbehov = sisteLøsteBehov?.definisjon?.tilAvklaringsbehovKode(),
            sisteSaksbehandlerSomLøstebehov = sisteLøsteBehov?.løstAv,
        ),
    )
}

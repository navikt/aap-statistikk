package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetBehandlingLagret
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.DiagnoseEntity
import no.nav.aap.statistikk.behandling.DiagnosePerioderRepository
import no.nav.aap.statistikk.behandling.DiagnoseRepository
import no.nav.aap.statistikk.beregningsgrunnlag.repository.IBeregningsgrunnlagRepository
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.jobber.appender.HendelsePublisher
import no.nav.aap.statistikk.jobber.appender.StatistikkHendelse
import no.nav.aap.statistikk.meldekort.FritaksvurderingRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.ITilkjentYtelseRepository
import no.nav.aap.statistikk.tilkjentytelse.repository.TilkjentYtelseEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.IVilkårsresultatRepository
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class AvsluttetBehandlingService(
    private val tilkjentYtelseRepository: ITilkjentYtelseRepository,
    private val beregningsgrunnlagRepository: IBeregningsgrunnlagRepository,
    private val vilkårsResultatRepository: IVilkårsresultatRepository,
    private val diagnoseRepository: DiagnoseRepository,
    private val diagnosePerioderRepository: DiagnosePerioderRepository,
    private val rettighetstypeperiodeRepository: IRettighetstypeperiodeRepository,
    private val fritaksvurderingRepository: FritaksvurderingRepository,
    private val behandlingService: BehandlingService,
    private val arbeidsopptrappingperioderRepository: ArbeidsopptrappingperioderRepository,
    private val institusjonsoppholdRepository: InstitusjonsoppholdRepository,
    private val vedtattStansOpphørRepository: VedtattStansOpphørRepository,
    private val samordningRepository: SamordningRepository,
    private val hendelsePublisher: HendelsePublisher,
) {
    private val logger = LoggerFactory.getLogger(AvsluttetBehandlingService::class.java)

    fun lagre(avsluttetBehandling: AvsluttetBehandling) {
        lagreDiagnose(avsluttetBehandling)

        val uthentetBehandling =
            behandlingService.hentBehandling(avsluttetBehandling.behandlingsReferanse)

        if (uthentetBehandling != null) {
            vilkårsResultatRepository
                .lagreVilkårsResultat(
                    VilkårsResultatEntity.fraDomene(avsluttetBehandling.vilkårsresultat),
                    uthentetBehandling.id()
                )
        } else {
            error("Ingen behandling med referanse ${avsluttetBehandling.behandlingsReferanse}.")
        }

        val skalLagre = skalLagre(uthentetBehandling, avsluttetBehandling)

        if (!skalLagre) {
            logger.info("Resultat var ${uthentetBehandling.resultat()} for behandling ${avsluttetBehandling.behandlingsReferanse}. Lagrer ikke.")
            return
        }

        val vedtakstidspunkt =
            vedtakstidspunktFor(avsluttetBehandling, uthentetBehandling.vedtakstidspunkt())
        tilkjentYtelseRepository.lagreTilkjentYtelse(
            tilkjentYtelsePåVedtaksdato(avsluttetBehandling, vedtakstidspunkt)
        )

        if (avsluttetBehandling.beregningsgrunnlag != null) {
            beregningsgrunnlagRepository.lagreBeregningsGrunnlag(
                MedBehandlingsreferanse(
                    value = avsluttetBehandling.beregningsgrunnlag,
                    behandlingsReferanse = avsluttetBehandling.behandlingsReferanse
                )
            )
        }

        arbeidsopptrappingperioderRepository.lagre(
            uthentetBehandling.id(),
            avsluttetBehandling.perioderMedArbeidsopptrapping
        )

        institusjonsoppholdRepository.lagre(
            uthentetBehandling.id(),
            avsluttetBehandling.institusjonsopphold
        )

        vedtattStansOpphørRepository.lagre(
            uthentetBehandling.id(),
            avsluttetBehandling.vedtattStansOpphør
        )

        samordningRepository.lagre(
            uthentetBehandling.id(),
            avsluttetBehandling.samordning
        )

        fritaksvurderingRepository.lagre(
            uthentetBehandling.id(),
            avsluttetBehandling.fritaksvurderinger
        )

        rettighetstypeperiodeRepository.lagre(
            avsluttetBehandling.behandlingsReferanse,
            avsluttetBehandling.rettighetstypeperioder
        )

        if (!behandlingService.erSkjermet(uthentetBehandling)) {
            hendelsePublisher.publiser(
                StatistikkHendelse.YtelsesstatistikkSkalLagres(
                    behandlingId = uthentetBehandling.id(),
                )
            )
        } else {
            logger.info("Lagrer ikke i BigQuery fordi noen i saken er skjermet.")
        }
        PrometheusProvider.prometheus.avsluttetBehandlingLagret().increment()
    }

    private fun skalLagre(
        uthentetBehandling: Behandling,
        avsluttetBehandling: AvsluttetBehandling
    ): Boolean {
        val resultat = uthentetBehandling.resultat()

        return when (resultat) {
            ResultatKode.AVBRUTT,
            ResultatKode.TRUKKET -> false

            ResultatKode.INNVILGET, ResultatKode.AVSLAG -> true
            ResultatKode.KLAGE_OPPRETTHOLDES,
            ResultatKode.KLAGE_OMGJØRES,
            ResultatKode.KLAGE_DELVIS_OMGJØRES,
            ResultatKode.KLAGE_AVSLÅTT,
            ResultatKode.KLAGE_TRUKKET -> error("Vil ikke oppstå.")

            null -> true.also { logger.info("Fant ikke resultat for behandling ${avsluttetBehandling.behandlingsReferanse}. Lagrer.") }
        }
    }

    private fun vedtakstidspunktFor(
        avsluttetBehandling: AvsluttetBehandling,
        lagretVedtakstidspunkt: LocalDateTime?
    ): LocalDateTime? {
        if (avsluttetBehandling.vedtakstidspunkt == null) {
            logger.warn("Vedtakstidspunkt mangler i avsluttet behandling for behandling ${avsluttetBehandling.behandlingsReferanse}.")
        }

        return avsluttetBehandling.vedtakstidspunkt ?: lagretVedtakstidspunkt
    }

    private fun tilkjentYtelsePåVedtaksdato(
        avsluttetBehandling: AvsluttetBehandling,
        vedtakstidspunkt: LocalDateTime?
    ): TilkjentYtelseEntity {
        return TilkjentYtelseEntity.fraDomene(
            avsluttetBehandling.tilkjentYtelse.begrensPerioderTil(
                requireNotNull(vedtakstidspunkt) {
                    "Vedtakstidspunkt mangler for behandling ${avsluttetBehandling.behandlingsReferanse}."
                }.toLocalDate()
            )
        )
    }

    private fun lagreDiagnose(avsluttetBehandling: AvsluttetBehandling) {
        if (avsluttetBehandling.diagnoser != null) {
            diagnoseRepository.lagre(
                DiagnoseEntity.fraDomene(
                    avsluttetBehandling.diagnoser.copy(bidiagnoser = avsluttetBehandling.diagnoser.bidiagnoser.filter { it != "INGEN_DIAGNOSE" }),
                    avsluttetBehandling.behandlingsReferanse
                )
            )
        }

        val behandling =
            behandlingService.hentBehandling(avsluttetBehandling.behandlingsReferanse)
        if (behandling != null) {
            diagnosePerioderRepository.lagre(
                behandling.id(),
                avsluttetBehandling.diagnoserPeriodisert
            )
        }
    }

}
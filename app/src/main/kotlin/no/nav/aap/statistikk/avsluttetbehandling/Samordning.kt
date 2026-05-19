package no.nav.aap.statistikk.avsluttetbehandling

import java.time.LocalDate

data class Samordning(
    val uføre: List<UførePeriode>,
    val statligeYtelser: List<StatligYtelse>,
    val avregningAndreYtelser: List<AvregningAndreYtelse>,
    val arbeidsgiver: List<Arbeidsgiver>,
) {
    data class UførePeriode(val fom: LocalDate, val tom: LocalDate, val grad: Int)

    data class StatligYtelse(
        val fom: LocalDate,
        val tom: LocalDate,
        val ytelse: SamordningYtelse,
        val prosent: Int,
    )

    enum class SamordningYtelse {
        SYKEPENGER,
        FORELDREPENGER,
        PLEIEPENGER,
        SVANGERSKAPSPENGER,
        OMSORGSPENGER,
        OPPLÆRINGSPENGER,
        FERIE_I_SYKEPENGEPERIODE,
    }

    data class AvregningAndreYtelse(
        val fom: LocalDate,
        val tom: LocalDate,
        val ytelse: AndreStatligeYtelse,
    )

    enum class AndreStatligeYtelse {
        SYKEPENGER,
        FORELDREPENGER,
        TILTAKSPENGER,
        OMSTILLINGSSTØNAD,
        OVERGANGSSTØNAD,
        DAGPENGER,
        BARNEPENSJON,
        GJENLEVENDEPENSJON,
    }

    data class Arbeidsgiver(val fom: LocalDate, val tom: LocalDate)
}

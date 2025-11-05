package no.nav.aap.statistikk.meldekort

import java.math.BigDecimal
import java.time.LocalDate

data class Meldekort(
    val journalpostId: String,
    val arbeidIPeriodeDTO: List<ArbeidIPerioder>
)


data class ArbeidIPerioder(
    val periodeFom: LocalDate,
    val periodeTom: LocalDate,
    val timerArbeidet: BigDecimal
)
package no.nav.aap.statistikk.meldekort

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.MeldekortDTO
import java.math.BigDecimal
import java.time.LocalDate

data class Meldekort(
    val journalpostId: String,
    val arbeidIPeriodeDTO: List<ArbeidIPerioder>
)


class ArbeidIPerioder(
    val periodeFom: LocalDate,
    val periodeTom: LocalDate,
    val timerArbeidet: BigDecimal
)
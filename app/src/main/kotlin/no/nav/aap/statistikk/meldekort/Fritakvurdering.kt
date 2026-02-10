package no.nav.aap.statistikk.meldekort

import java.time.LocalDate

data class Fritakvurdering(
    val harFritak: Boolean,
    val fraDato: LocalDate,
    val tilDato: LocalDate? = null,
)
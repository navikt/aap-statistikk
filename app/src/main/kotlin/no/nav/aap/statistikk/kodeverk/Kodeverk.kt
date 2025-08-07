package no.nav.aap.statistikk.kodeverk

import java.time.LocalDate

data class Kodeverk(
    val kode: String,
    val beskrivelse: String,
    val gyldigFra: LocalDate,
    val gyldigTil: LocalDate?
)
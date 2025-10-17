package no.nav.aap.statistikk.api

import no.nav.aap.komponenter.config.requiredConfigForKey
import java.util.UUID

enum class Azp(val uuid: UUID) {
    Postmottak(UUID.fromString(requiredConfigForKey("integrasjon.postmottak.azp"))),
    Behandlingsflyt(UUID.fromString(requiredConfigForKey("integrasjon.behandlingsflyt.azp"))),
    Oppgave(UUID.fromString(requiredConfigForKey("integrasjon.oppgave.azp")))
}
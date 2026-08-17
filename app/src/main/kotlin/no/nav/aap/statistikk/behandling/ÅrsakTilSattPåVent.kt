package no.nav.aap.statistikk.behandling

/**
 * Speiler [no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent] fra kontrakten, men med
 * en kortere kode ([kortKode]) for verdiene som ellers gjør at `behandling_status` i saksstatistikk
 * blir for lang for Team Sak. For verdier som allerede er korte nok, er [kortKode] lik det opprinnelige navnet.
 */
enum class ÅrsakTilSattPåVent(val kortKode: String) {
    VENTER_PÅ_OPPLYSNINGER(kortKode = "VENTER_PÅ_OPPLYSNINGER"),
    VENTER_PÅ_OPPLYSNINGER_FRA_UTENLANDSKE_MYNDIGHETER(kortKode = "VENTER_PÅ_UTENLANDSKE_OPPLYSNINGER"),
    VENTER_PÅ_MEDISINSKE_OPPLYSNINGER(kortKode = "VENTER_PÅ_MEDISINSKE_OPPLYSNINGER"),
    VENTER_PÅ_VURDERING_AV_ROL(kortKode = "VENTER_PÅ_VURDERING_AV_ROL"),
    VENTER_PÅ_SVAR_FRA_BRUKER(kortKode = "VENTER_PÅ_SVAR_FRA_BRUKER"),
    VENTER_PÅ_MASKINELL_AVKLARING(kortKode = "VENTER_PÅ_MASKINELL_AVKLARING"),
    VENTER_PÅ_UTENLANDSK_VIDEREFORING_AVKLARING(kortKode = "VENTER_PÅ_UTENLANDSK_VIDEREFORING_AVKLARING"),
    VENTER_PÅ_KLAGE_IMPLEMENTASJON(kortKode = "VENTER_PÅ_KLAGE_IMPLEMENTASJON"),
    VENTER_PÅ_SVAR_PÅ_FORHÅNDSVARSEL(kortKode = "VENTER_PÅ_SVAR_PÅ_FORHÅNDSVARSEL"),
    VENTER_PÅ_FUNKSJONALITET_AVSLAG_11_27(kortKode = "VENTER_PÅ_FUNKSJONALITET_AVSLAG_11_27"),
    VENTER_PÅ_FUNKSJONALITET(kortKode = "VENTER_PÅ_FUNKSJONALITET");

    companion object {
        /**
         * [behandling.venteÅrsak][Behandling.venteÅrsak] er lagret som fritekst (speiler ikke
         * nødvendigvis en kjent verdi, f.eks. i tester). Ukjente verdier gis tilbake uendret.
         */
        fun kortKodeFor(venteÅrsak: String): String =
            entries.firstOrNull { it.name == venteÅrsak }?.kortKode ?: venteÅrsak
    }
}

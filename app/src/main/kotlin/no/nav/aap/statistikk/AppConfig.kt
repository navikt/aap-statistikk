package no.nav.aap.statistikk

import kotlin.time.Duration.Companion.seconds

object AppConfig {
    // Matcher terminationGracePeriodSeconds for podden i Kubernetes-manifestet ("nais.yaml")
    private val kubernetesTimeout = 30.seconds

    // Tid før ktor avslutter uansett. Må være litt mindre enn `kubernetesTimeout`.
    val shutdownTimeout = kubernetesTimeout - 2.seconds

    // Tid appen får til å fullføre påbegynte requests, jobber etc. Må være mindre enn `endeligShutdownTimeout`.
    val shutdownGracePeriod = shutdownTimeout - 3.seconds

    // Tid appen får til å avslutte Motor, Kafka, etc
    val stansArbeidTimeout = shutdownGracePeriod - 1.seconds

    // Vi skrur opp ktor sin default-verdi, som er "antall CPUer", fordi vi har en del venting på IO (db, kafka, http):
    private const val ktorParallellitet = 8

    // Vi følger *IKKE* ktor sin metodikk for å regne ut tuning parametre for callGroupSize. Vi
    // har ikke async IO, hverken for utadgående HTTP-kall eller mot databasen, så vi trenger betydelig flere
    // tråder enn en async kodebase.
    const val callGroupSize = 64

    const val ANTALL_WORKERS_FOR_MOTOR = 4
    const val hikariMaxPoolSize = ktorParallellitet + 2 * ANTALL_WORKERS_FOR_MOTOR
}
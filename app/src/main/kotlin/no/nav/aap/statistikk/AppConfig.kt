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

    // Vi følger ktor sin metodikk for å regne ut tuning parametre som funksjon av parallellitet
    // https://github.com/ktorio/ktor/blob/3.3.1/ktor-server/ktor-server-core/common/src/io/ktor/server/engine/ApplicationEngine.kt#L30
    const val connectionGroupSize = ktorParallellitet / 2 + 1
    const val workerGroupSize = ktorParallellitet / 2 + 1
    const val callGroupSize = 4 * ktorParallellitet

    const val ANTALL_WORKERS_FOR_MOTOR = 4
    const val hikariMaxPoolSize = ktorParallellitet + 2 * ANTALL_WORKERS_FOR_MOTOR
}
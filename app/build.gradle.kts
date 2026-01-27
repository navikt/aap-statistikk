import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap.conventions")
    kotlin("jvm")
    alias(libs.plugins.ktor)
    id("dev.detekt")
    application
}

detekt {
    ignoreFailures = true
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("21")
}

val mockkVersion = "1.14.7"
val flywayVersion = "11.14.0"
val testContainersVersion = "2.0.3"

application {
    mainClass.set("no.nav.aap.statistikk.AppKt")
}

tasks.register<JavaExec>("runTestApp") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.statistikk.TestAppKt")
    workingDir = rootDir
}

tasks.register<JavaExec>("genererOpenApi") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.statistikk.GenererOpenApiJsonKt")
    workingDir = rootDir
}


dependencies {
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorServerHtmlBuilder)

    implementation(libs.logback)
    implementation(libs.logbackLogstashEncoder)

    implementation(libs.flyway)
    implementation(libs.flywayPostgres)
    runtimeOnly("org.postgresql:postgresql:42.7.9")
    implementation("com.zaxxer:HikariCP:7.0.2")

    implementation(libs.motor)
    implementation(libs.motorApi)
    implementation(libs.httpklient)
    implementation(libs.dbconnect)
    implementation(libs.infrastructure)
    implementation(libs.server)

    implementation(libs.behandlingsflytKontrakt)
    implementation(libs.tilgangKontrakt)
    implementation(libs.caffeine)
    api(libs.tilgangPlugin)

    implementation(libs.oppgaveKontrakt)
    implementation(libs.postmottakKontrakt)
    implementation(libs.utbetalKontrakt)

    implementation("com.google.cloud:google-cloud-bigquery:2.57.2")

    testImplementation(libs.motorTestUtils)
    testImplementation(libs.ktorServerTestHost)
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.7")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.testcontainers:testcontainers-postgresql:${testContainersVersion}")
    testImplementation("org.testcontainers:testcontainers-gcloud:${testContainersVersion}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${testContainersVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    withType<ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }

}

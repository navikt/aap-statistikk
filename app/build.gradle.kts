import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap.conventions")
    kotlin("jvm")
    alias(kelvinLibs.plugins.ktor)
    alias(libs.plugins.detektGradlePlugin)
    application
}

detekt {
    ignoreFailures = true
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("21")
}

application {
    mainClass.set("no.nav.aap.statistikk.AppKt")
}

tasks.register<JavaExec>("runTestApp") {
    group = "application"
    description = "Kjør TestApp."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.statistikk.TestAppKt")
    workingDir = rootDir
}

tasks.register<JavaExec>("genererOpenApi") {
    group = "documentation"
    description = "Generer OpenAPI JSON, lagre i openapi.json i rotmappen."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.statistikk.GenererOpenApiJsonKt")
    workingDir = rootDir
}


dependencies {
    implementation("io.ktor:ktor-server-html-builder:${kelvinLibs.versions.ktor.get()}")
    implementation(kelvinLibs.logback.classic)
    implementation(kelvinLibs.logstash.logback.encoder)

    implementation(kelvinLibs.hikaricp)

    implementation(libs.motor)
    implementation(libs.motorApi)
    implementation(libs.httpklient)
    implementation(libs.dbconnect)
    implementation(libs.dbmigrering)
    implementation(libs.infrastructure)
    implementation(libs.server)

    implementation(libs.behandlingsflytKontrakt)
    implementation(libs.tilgangKontrakt)
    implementation(kelvinLibs.caffeine)
    api(libs.tilgangPlugin)

    implementation(libs.oppgaveKontrakt)
    implementation(libs.postmottakKontrakt)
    implementation(libs.utbetalKontrakt)

    implementation(libs.googleCloudBigquery)

    testImplementation(libs.motorTestUtils)
    testImplementation(kelvinLibs.ktor.server.test.host)
    testImplementation(kelvinLibs.nimbus.jose.jwt)
    testImplementation(kelvinLibs.mockk)
    testImplementation(kelvinLibs.assertj.core)
    testImplementation(kelvinLibs.testcontainers.postgresql)
    testImplementation("org.testcontainers:testcontainers-gcloud:${kelvinLibs.versions.testcontainers.get()}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${kelvinLibs.versions.testcontainers.get()}")
    testImplementation(kelvinLibs.bundles.junit)
}

tasks {
    withType<ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }

}

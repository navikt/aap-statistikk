import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap.conventions")
    kotlin("jvm")
    alias(libs.plugins.ktor)
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
    runtimeOnly(libs.postgresql)
    implementation(libs.hikaricp)

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

    implementation(libs.googleCloudBigquery)

    testImplementation(libs.motorTestUtils)
    testImplementation(libs.ktorServerTestHost)
    testImplementation(libs.nimbusJoseJwt)
    testImplementation(libs.mockk)
    testImplementation(libs.assertj)
    testImplementation(libs.testcontainersPostgresql)
    testImplementation(libs.testcontainersGcloud)
    testImplementation(libs.testcontainersJunit)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks {
    withType<ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }

}

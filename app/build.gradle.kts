import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap-statistikk.conventions")
    kotlin("jvm")
    id("io.ktor.plugin") version "3.2.3"
    application
}

val ktorVersion = "3.2.3"
val mockkVersion = "1.14.5"
val flywayVersion = "11.11.2"
val testContainersVersion = "1.21.3"
val komponenterVersjon = "1.0.339"
val behandlingsflytversjon = "0.0.415"
val tilgangVersjon = "1.0.115"
val oppgaveVersjon = "0.0.115"
val postmottakVersjon = "0.0.116"
val utbetalVersjon = "0.0.56"

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
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:${ktorVersion}")

    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    runtimeOnly("org.postgresql:postgresql:42.7.7")
    implementation("com.zaxxer:HikariCP:7.0.2")

    implementation("no.nav.aap.kelvin:motor:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor-api:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbconnect:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:infrastructure:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:server:$komponenterVersjon")

    implementation("no.nav:ktor-openapi-generator:1.0.122")
    implementation("no.nav.aap.behandlingsflyt:kontrakt:$behandlingsflytversjon")
    implementation("no.nav.aap.tilgang:api-kontrakt:$tilgangVersjon")
    api("no.nav.aap.tilgang:plugin:${tilgangVersjon}")

    implementation("no.nav.aap.oppgave:api-kontrakt:$oppgaveVersjon")
    implementation("no.nav.aap.postmottak:kontrakt:$postmottakVersjon")
    implementation("no.nav.aap.utbetal:api-kontrakt:$utbetalVersjon")

    implementation("com.google.cloud:google-cloud-bigquery:2.54.1")


    testImplementation(kotlin("test"))
    testImplementation("no.nav.aap.kelvin:motor-test-utils:$komponenterVersjon")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.4.2")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
        implementation("org.apache.commons:commons-lang3:3.18.0") {
            because("https://www.mend.io/vulnerability-database/CVE-2025-48924?utm_source=JetBrains")
        }
    }
    testImplementation("org.testcontainers:gcloud:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

tasks {
    withType<ShadowJar> {
        mergeServiceFiles()
    }
}

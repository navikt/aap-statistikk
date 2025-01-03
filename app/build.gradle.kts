import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap-statistikk.conventions")
    kotlin("jvm")
    id("io.ktor.plugin") version "3.0.2"
    application
}

val ktorVersion = "3.0.3"
val mockkVersion = "1.13.14"
val komponenterVersjon = "1.0.101"
val behandlingsflytversjon = "0.0.68"
val flywayVersion = "11.1.0"
val oppgaveVersjon = "0.0.39"

application {
    mainClass.set("no.nav.aap.statistikk.AppKt")
}

tasks.register<JavaExec>("runTestApp") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.statistikk.TestAppKt")
    workingDir = rootDir
}

dependencies {
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    constraints {
        implementation("io.netty:netty-common:4.1.115.Final")
    }

    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:1.5.15")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    runtimeOnly("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.2.1")

    implementation("no.nav.aap.kelvin:motor:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor-api:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbconnect:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:server:$komponenterVersjon")

    implementation("no.nav:ktor-openapi-generator:1.0.72")
    implementation("no.nav.aap.behandlingsflyt:kontrakt:$behandlingsflytversjon")
    implementation("no.nav.aap.oppgave:api-kontrakt:$oppgaveVersjon")
    implementation("no.nav.aap.postmottak:kontrakt:0.0.21")

    implementation("com.google.cloud:google-cloud-bigquery:2.45.0")


    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.0")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("org.assertj:assertj-core:3.27.1")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    constraints {
        implementation("org.apache.commons:commons-compress:1.27.1") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation("org.testcontainers:gcloud:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap-statistikk.conventions")
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.12"
    application
}

val ktorVersion = "2.3.12"
val mockkVersion = "1.13.12"
val komponenterVersjon = "1.0.11"

application {
    mainClass.set("no.nav.aap.statistikk.AppKt")
}


dependencies {
    implementation(project(":api-kontrakt"))
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:1.5.9")
    constraints {
        implementation("org.apache.commons:commons-compress:1.26.0")
    }
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation("org.flywaydb:flyway-database-postgresql:10.18.2")
    runtimeOnly("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.0.0")

    implementation("no.nav.aap.kelvin:motor:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor-api:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbconnect:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:server:$komponenterVersjon")

    implementation("no.nav:ktor-openapi-generator:1.0.32")
    implementation("com.google.cloud:google-cloud-bigquery:2.42.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.41.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.testcontainers:postgresql:1.20.1")
    testImplementation("org.testcontainers:gcloud:1.20.2")
    testImplementation("org.testcontainers:junit-jupiter:1.20.2")
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

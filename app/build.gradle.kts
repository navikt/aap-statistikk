import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap-statistikk.conventions")
    kotlin("jvm")
    alias(libs.plugins.ktor)
    id("io.gitlab.arturbosch.detekt")
    application
}

val mockkVersion = "1.14.6"
val flywayVersion = "11.14.0"
val testContainersVersion = "1.21.3"

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
    runtimeOnly("org.postgresql:postgresql:42.7.8")
    implementation("com.zaxxer:HikariCP:7.0.2")

    implementation(libs.motor)
    implementation(libs.motorApi)
    implementation(libs.httpklient)
    implementation(libs.dbconnect)
    implementation(libs.infrastructure)
    implementation(libs.server)

    implementation("no.nav:ktor-openapi-generator:1.0.131")
    implementation(libs.behandlingsflytKontrakt)
    implementation(libs.tilgangKontrakt)
    api(libs.tilgangPlugin)

    implementation(libs.oppgaveKontrakt)
    implementation(libs.postmottakKontrakt)
    implementation(libs.utbetalKontrakt)

    implementation("com.google.cloud:google-cloud-bigquery:2.55.2")

    testImplementation(libs.motorTestUtils)
    testImplementation(libs.ktorServerTestHost)
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.5")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
        implementation("org.apache.commons:commons-lang3:3.19.0") {
            because("https://www.mend.io/vulnerability-database/CVE-2025-48924?utm_source=JetBrains")
        }
    }
    testImplementation("org.testcontainers:gcloud:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    mavenLocal()
}

tasks {
    withType<ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }

    test {
        useJUnitPlatform()
        maxParallelForks = Runtime.getRuntime().availableProcessors() / 2
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
}

kotlin {
    jvmToolchain(21)
}

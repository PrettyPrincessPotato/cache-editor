plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "org.scape"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("io.netty:netty-all:4.2.7.Final")
    implementation("com.displee:rs-cache-library:8.0.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.11.1")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "editor.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
    val cachePath = findProperty("cachePath") as String? ?: "../2011Scape-2/cache"
    systemProperty("cache.path", file(cachePath).absolutePath)
}

description = "Locale-aware formatter cache (numbers, dates, lists) for Kotlin Multiplatform"

plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("de.infix.testBalloon") version "1.0.1-K2.4.0"
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:atomicfu:0.32.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
        }
    }
}

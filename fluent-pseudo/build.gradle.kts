description = "Pseudolocalization transforms (Accented, Bidi, Long, Widened, Hidden) for catching hard-coded strings"

plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("de.infix.testBalloon")
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation(project(":fluent-bundle"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":intl-memoizer"))
            implementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
        }
    }
}

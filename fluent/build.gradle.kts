description = "Public umbrella module: re-exports fluent-bundle, fluent-pseudo, fluent-fallback entry points"

plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation(project(":fluent-bundle"))
            implementation(project(":fluent-syntax"))
            implementation(project(":intl-memoizer"))
            implementation(project(":fluent-fallback"))
            implementation(project(":fluent-resmgr"))
            implementation(project(":fluent-pseudo"))
        }
    }
}

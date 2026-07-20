description = "Public umbrella module: re-exports fluent-bundle, fluent-pseudo, fluent-fallback entry points"

plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":fluent-bundle"))
            api(project(":intl-memoizer"))
            api(project(":fluent-pseudo"))
            api(project(":fluent-fallback"))
            api(project(":fluent-resmgr"))
            api(project(":fluent-syntax"))
        }
    }
}

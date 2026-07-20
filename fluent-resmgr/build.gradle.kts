description = "Filesystem resource loader for Fluent bundles with locale-tag -> language -> base path resolution"

plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("de.infix.testBalloon")
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation(project(":fluent-bundle"))
            implementation(project(":fluent-fallback"))
            implementation(project(":intl-memoizer"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
            }
        }
    }
}

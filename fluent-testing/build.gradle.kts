description = "Test helpers and shared fixtures (loads fluent-rs upstream test data)"

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
            implementation(project(":fluent-syntax"))
            implementation(project(":fluent-bundle"))
            implementation(project(":fluent-fallback"))
            implementation(project(":fluent-resmgr"))
            implementation(project(":intl-memoizer"))
        }
        jvmMain.dependencies {
            implementation("com.charleskorn.kaml:kaml:0.104.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
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

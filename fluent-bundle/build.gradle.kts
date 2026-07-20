description = "FluentBundle runtime: resolver, message/term lookup, custom functions, builder DSL"

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
            implementation(project(":intl-memoizer"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":fluent-testing"))
            implementation("com.charleskorn.kaml:kaml:0.104.0")
            implementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
            implementation("io.kotest:kotest-property:5.9.1")
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
            }
            resources.srcDirs("src/commonTest/resources")
        }
    }
}

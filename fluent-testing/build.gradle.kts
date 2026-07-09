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
            implementation(project(":fluent-syntax"))
            implementation(project(":fluent-bundle"))
            implementation(project(":fluent-fallback"))
            implementation(project(":fluent-resmgr"))
            implementation(project(":intl-memoizer"))
            implementation("com.charleskorn.kaml:kaml:0.70.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}

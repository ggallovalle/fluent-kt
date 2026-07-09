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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":fluent-testing"))
        }
        getByName("jvmTest") {
            dependsOn(getByName("commonTest"))
            dependencies {
                implementation(kotlin("test"))
            }
            resources.srcDirs("src/commonTest/resources")
        }
    }
}

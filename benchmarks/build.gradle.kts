import kotlinx.benchmark.gradle.JvmBenchmarkTarget

description = "Microbenchmarks for fluent-kt (kotlinx-benchmark / JMH)"

plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.allopen") version "2.4.10"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.17"
}

// JMH requires @State classes to be open; allopen opens them at compile time.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":fluent-syntax"))
            implementation(project(":fluent-bundle"))
            implementation(project(":intl-memoizer"))
            implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.17")
        }
    }
}

benchmark {
    targets {
        register("jvm") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.37"
        }
        register("linuxX64")
    }

    configurations {
        // Default profile — thorough enough for local comparison runs.
        named("main") {
            iterations = 5
            warmups = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        // Quick sanity check that benchmarks compile and run.
        register("smoke") {
            iterations = 1
            warmups = 0
            iterationTime = 100
            iterationTimeUnit = "ms"
            advanced("jvmForks", 1)
        }
    }
}

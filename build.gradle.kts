// Top-level build file for fluent-kt
plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    id("com.android.library") version "8.9.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("de.infix.testBalloon") version "1.0.1-K2.4.0" apply false
    id("dev.detekt") version "2.0.0-alpha.5" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    val detektVersion = libs.findVersion("detekt").get().requiredVersion
    val detektKotlinVersion = libs.findVersion("detektKotlin").get().requiredVersion
    val detektJvmTarget = libs.findVersion("detektJvmTarget").get().requiredVersion

    apply<dev.detekt.gradle.plugin.DetektPlugin>()

    afterEvaluate {
        configurations.matching { it.name == "detekt" }.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(detektKotlinVersion)
                }
            }
        }
    }

    dependencies {
        add("detektPlugins", "dev.detekt:detekt-rules-ktlint-wrapper:$detektVersion")
    }

    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        toolVersion = detektVersion
        config.setFrom(rootProject.layout.projectDirectory.file("detekt.yml"))
        buildUponDefaultConfig = true
        ignoreFailures = false
    }

    tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
        jvmTarget = detektJvmTarget
        // Auto-correct can be enabled by running `./gradlew detektAll -PdetektAutoCorrect=true`.
        if (project.findProperty("detektAutoCorrect") == "true") {
            autoCorrect = true
        }
        reports {
            html.required.set(true)
            sarif.required.set(true)
            markdown.required.set(true)
            checkstyle.required.set(true)
        }
    }
    tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
        jvmTarget = detektJvmTarget
    }

    // The detekt plugin does not aggregate per-source-set detekt tasks in
    // Kotlin Multiplatform projects. Expose a `detektAll` task that
    // depends on every `detekt*` task in the project so `./gradlew detektAll`
    // runs the lot.
    //
    // We wire the dependencies inside `subprojects { afterEvaluate { ... } }`
    // (not inside a `gradle.taskGraph.whenReady` callback) so the per-source-
    // set detekt tasks are part of the task graph at task-graph build time
    // and `--rerun-tasks` applies to them.
    val detektAll = tasks.register("detektAll") {
        group = "verification"
        description = "Runs all detekt analyses (per source set and with type resolution)"
    }
    afterEvaluate {
        tasks.withType<dev.detekt.gradle.Detekt>().forEach { detektTask ->
            if (detektTask.name != "detektAll") {
                detektAll.configure { dependsOn(detektTask) }
            }
        }
    }

    tasks.withType<Test>().configureEach {
        timeout.set(java.time.Duration.ofSeconds(10))
    }
}

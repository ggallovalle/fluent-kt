import java.net.URI
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

// Top-level build file for fluent-kt
plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    id("com.android.library") version "9.3.0" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("de.infix.testBalloon") version "1.0.1-K2.4.0" apply false
    id("dev.detekt") version "2.0.0-alpha.5" apply false
    id("org.jetbrains.dokka") version "2.2.0"
}

// Aggregate public API docs from every publishing module into one HTML site.
dokka {
    dokkaPublications.html {
        moduleName.set("fluent-kt")
        includes.from("README.md")
    }
}

dependencies {
    dokka(project(":fluent-syntax"))
    dokka(project(":intl-memoizer"))
    dokka(project(":fluent-bundle"))
    dokka(project(":fluent-pseudo"))
    dokka(project(":fluent-fallback"))
    dokka(project(":fluent-resmgr"))
    dokka(project(":fluent"))
}

// Apply the vanniktech publish plugin to every publishing module
// (excludes internal modules: :fluent-testing, :benchmarks).
// The plugin's base extension is configured with shared POM metadata
// pulled from gradle.properties.
subprojects {
    if (project.path !in setOf(":fluent-testing", ":benchmarks")) {
        apply(plugin = "com.vanniktech.maven.publish")
        apply(plugin = "org.jetbrains.dokka")
        extensions.configure(com.vanniktech.maven.publish.MavenPublishBaseExtension::class.java) {
            publishToMavenCentral()
            signAllPublications()
            pom {
                name.set(project.findProperty("POM_NAME") as String? ?: "fluent-kt")
                description.set(project.findProperty("POM_DESCRIPTION") as String? ?: "")
                inceptionYear.set(project.findProperty("POM_INCEPTION_YEAR") as String? ?: "2026")
                url.set(project.findProperty("POM_URL") as String? ?: "")
                licenses {
                    license {
                        name.set(project.findProperty("POM_LICENSE_NAME") as String? ?: "MIT")
                        url.set(project.findProperty("POM_LICENSE_URL") as String? ?: "")
                        distribution.set(project.findProperty("POM_LICENSE_DISTRIBUTION") as String? ?: "")
                    }
                }
                developers {
                    developer {
                        id.set(project.findProperty("POM_DEVELOPER_ID") as String? ?: "")
                        name.set(project.findProperty("POM_DEVELOPER_NAME") as String? ?: "")
                        url.set(project.findProperty("POM_DEVELOPER_URL") as String? ?: "")
                    }
                }
                scm {
                    url.set(project.findProperty("POM_SCM_URL") as String? ?: "")
                    connection.set(project.findProperty("POM_SCM_CONNECTION") as String? ?: "")
                    developerConnection.set(project.findProperty("POM_SCM_DEV_CONNECTION") as String? ?: "")
                }
            }
        }
        extensions.configure<DokkaExtension>("dokka") {
            modulePath.set(project.name)
            dokkaSourceSets.configureEach {
                documentedVisibilities.set(setOf(VisibilityModifier.Public))
                sourceLink {
                    localDirectory.set(project.file("src"))
                    remoteUrl.set(
                        URI("https://github.com/ggallovalle/fluent-kt/tree/main/${project.name}/src"),
                    )
                    remoteLineSuffix.set("#L")
                }
            }
        }
    }
}

// `clean` is provided by the base plugin once Dokka is applied at the root.

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
    val detektAll = tasks.register("detektAll") {
        group = "verification"
        description = "Runs all detekt analyses (per source set and with type resolution)"
    }
    afterEvaluate {
        tasks.withType<dev.detekt.gradle.Detekt>().forEach { detektTask ->
            if (detektTask.name != detektAll.name) {
                detektAll.configure { dependsOn(detektTask) }
            }
        }
    }

    tasks.withType<Test>().configureEach {
        timeout.set(java.time.Duration.ofSeconds(10))
    }
}

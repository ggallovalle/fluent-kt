import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

description = "Example Android app using fluent-compose + fluent codegen"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * JavaExec wrapper with a [DirectoryProperty] so AGP can wire generated Kotlin
 * via [com.android.build.api.variant.SourceDirectories.addGeneratedSourceDirectory].
 *
 * In-repo samples cannot apply `id("dev.kbroom.fluent")` via a project dependency
 * in `buildscript`; published consumers use the Gradle plugin instead.
 */
abstract class FluentGenerateExec : JavaExec() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

android {
    namespace = "dev.kbroom.fluent.examples.androidcompose"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.kbroom.fluent.examples.androidcompose"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

val fluentCodegenRuntime = configurations.create("fluentCodegenRuntime")

dependencies {
    add("fluentCodegenRuntime", project(":fluent-codegen"))
    implementation(project(":fluent-compose"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.appcompat)
}

val fluentGenerate = tasks.register("fluentGenerate", FluentGenerateExec::class.java) {
    group = "fluent"
    description = "Generate typed Fluent accessors for the example app"
    classpath = fluentCodegenRuntime
    mainClass.set("dev.kbroom.fluent.codegen.FluentCodegenMain")
    // Android toolchain is 17; codegen was compiled with the repo JDK (21+).
    val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
    javaLauncher.set(
        toolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(JavaVersion.current().majorVersion))
        },
    )
    outputDir.convention(layout.buildDirectory.dir("generated/fluent/kotlin"))
    val sourceDir = layout.projectDirectory.dir("src/main/assets/i18n")
    inputs.dir(sourceDir)
    argumentProviders.add {
        listOf(
            sourceDir.asFile.absolutePath,
            outputDir.get().asFile.absolutePath,
            "dev.kbroom.fluent.examples.androidcompose.i18n",
            "true",
        )
    }
}

extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            fluentGenerate,
            FluentGenerateExec::outputDir,
        )
    }
}

tasks.named("preBuild").configure { dependsOn(fluentGenerate) }

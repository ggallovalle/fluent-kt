description = "Gradle plugin: validate / generate / scaffold Fluent FTL resources"

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    id("de.infix.testBalloon")
}

dependencies {
    implementation(project(":fluent-codegen"))
    implementation(project(":fluent-syntax"))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    compileOnly("com.android.tools.build:gradle:9.3.0")
    testImplementation(kotlin("test"))
    testImplementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("fluent") {
            id = "io.github.ggallovalle.fluent"
            displayName = "Fluent FTL codegen"
            description =
                "Validates Fluent FTL trees and generates typed Kotlin accessors " +
                    "(FluentBundle + Localization wrappers)."
            implementationClass = "dev.kbroom.fluent.gradle.FluentPlugin"
        }
    }
}

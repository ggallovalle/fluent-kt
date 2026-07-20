description = "AST-driven Fluent codegen (Kotlin emitters, locale scaffold, validation)"

plugins {
    kotlin("jvm")
    id("de.infix.testBalloon")
}

dependencies {
    implementation(project(":fluent-syntax"))
    testImplementation(kotlin("test"))
    testImplementation("de.infix.testBalloon:testBalloon-framework-core:1.0.1-K2.4.0")
}

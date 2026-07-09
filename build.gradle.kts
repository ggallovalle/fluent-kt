// Top-level build file for fluent-kt
plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    id("com.android.library") version "8.9.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("de.infix.testBalloon") version "1.0.1-K2.4.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
subprojects {
    tasks.withType<Test>().configureEach {
        timeout.set(java.time.Duration.ofSeconds(10))
    }
}

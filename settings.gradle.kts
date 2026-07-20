pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fluent-kt"
include("fluent-syntax")
include("intl-memoizer")
include("fluent-bundle")
include("fluent-pseudo")
include("fluent-fallback")
include("fluent-resmgr")
include("fluent-testing")
include("fluent")
include("fluent-codegen")
include("fluent-gradle-plugin")
include("fluent-compose")
include("benchmarks")
include(":examples:android-compose")
project(":examples:android-compose").projectDir = file("examples/android-compose")

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

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // OwnTV's own Maven repository — public, no login: tv.own.owntv:core and :player-core
        // (built from https://github.com/ahXN00/OwnTV_Core) and tv.own.owntv:libmpv, the mpv engine
        // (https://github.com/ahXN00/OwnTV_libmpv). Served from OwnTV_Core's gh-pages branch.
        maven {
            name = "OwnTV"
            url = uri("https://ahxn00.github.io/OwnTV_Core/maven")
            content { includeGroup("tv.own.owntv") }
        }
    }
}

// Build this fork against its accompanying modified core sources. The composite build
// substitutes matching group/artifact IDs; keep both source directories side by side.
includeBuild("../veylora-core")

rootProject.name = "VeyloraTV"
include(":app")
// Baseline-profile generator (audit ST1). Test-only module: it ships nothing to users, it records
// the cold-start journey on a device and writes the profile :app packages.
include(":baselineprofile")

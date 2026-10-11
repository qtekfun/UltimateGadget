pluginManagement {
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx\\..*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx\\..*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "Gadgetbridge"

// The map engine lives in its own repo (shared with UltimateMaps), consumed here as a Gradle
// composite build from the ultimate-mapcore git submodule. Gradle auto-substitutes the
// "com.qtekfun:mapcore" dependency with the submodule's :mapcore project, so the app builds it
// from source (no published artifact needed). Clone with --recursive, or run
// `git submodule update --init`.
includeBuild("ultimate-mapcore")

include(":app")
include(":GBDaoGenerator")
include(":FitCodeGenerator")
include(":KaitaiAuxGenerator")
include(":GBProtobufGenerator")
include(":mapdownloader")

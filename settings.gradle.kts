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
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Official Artifex Ghostscript Maven repository for MuPDF JNI artifact (com.artifex.mupdf:fitz)
        maven {
            url = uri("http://maven.ghostscript.com")
            isAllowInsecureProtocol = true
        }
    }
}

rootProject.name = "OfflinePdfStudio"

include(":app")
include(":core:common")
include(":core:design-system")
include(":core:database")
include(":core:storage")
include(":core:pdf")
include(":core:image-processing")
include(":core:ocr")
include(":feature:home")
include(":feature:files")
include(":feature:reader")
include(":feature:scanner")
include(":feature:tools")
include(":feature:settings")

rootProject.name = "InComedy"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
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
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://artifactory-external.vkpartner.ru/artifactory/vkid-sdk-android/")
        }
        maven {
            url = uri("https://artifactory-external.vkpartner.ru/artifactory/maven/")
        }
        maven {
            url = uri("https://artifactory-external.vkpartner.ru/artifactory/vk-id-captcha/android/")
        }
    }
}

include(":composeApp")
include(":shared")
include(":core:common")
include(":core:backend")
include(":domain:auth")
include(":domain:session")
include(":feature:auth")
include(":data:auth")
include(":data:session")
include(":server")

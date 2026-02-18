plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.bam.incomedy.buildlogic"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:2.3.10")
    implementation("com.android.kotlin.multiplatform.library:com.android.kotlin.multiplatform.library.gradle.plugin:9.0.1")
}

gradlePlugin {
    plugins {
        register("incomedyKmpLibrary") {
            id = "incomedy.kmp.library"
            implementationClass = "InComedyKmpLibraryConventionPlugin"
        }
        register("incomedyFeature") {
            id = "incomedy.feature"
            implementationClass = "InComedyFeatureConventionPlugin"
        }
        register("incomedyData") {
            id = "incomedy.data"
            implementationClass = "InComedyDataConventionPlugin"
        }
    }
}

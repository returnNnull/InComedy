import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("incomedy.kmp.library")
}

kotlin {
    androidLibrary {
        namespace = "com.bam.incomedy.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":data:auth"))
            implementation(project(":data:session"))
            implementation(project(":data:venue"))
            implementation(project(":domain:auth"))
            implementation(project(":domain:session"))
            implementation(project(":domain:venue"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:venue"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

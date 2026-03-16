plugins {
    id("incomedy.feature")
}

kotlin {
    androidLibrary {
        namespace = "com.bam.incomedy.feature.auth"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:auth"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(project(":domain:auth"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

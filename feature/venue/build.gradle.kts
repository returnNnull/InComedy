plugins {
    id("incomedy.feature")
}

kotlin {
    androidLibrary {
        namespace = "com.bam.incomedy.feature.venue"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:venue"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(project(":domain:venue"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

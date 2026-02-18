plugins {
    id("incomedy.feature")
}

kotlin {
    androidLibrary {
        namespace = "com.bam.auth"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {

        }
    }
}

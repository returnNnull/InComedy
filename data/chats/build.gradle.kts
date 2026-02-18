plugins {
    id("incomedy.data")
}

kotlin {
    androidLibrary {
        namespace = "com.bam.incomedy.data.chats"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    sourceSets {
        commonMain.dependencies {

        }
    }
}

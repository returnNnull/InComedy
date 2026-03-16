plugins {
    id("incomedy.kmp.library")
}

kotlin {
    androidLibrary {
        namespace = "com.bam.incomedy.domain.session"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

plugins {
    id("incomedy.kmp.library")
}

kotlin {
    jvmToolchain(17)
    jvm()

    androidLibrary {
        namespace = "com.bam.incomedy.domain.lineup"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

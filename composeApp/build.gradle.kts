import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val releaseSigningPropertiesFile = rootProject.file("signing/android/release-signing.properties")
val releaseKeystoreFile = rootProject.file("signing/android/incomedy-release.jks")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

fun signingProperty(name: String): String? {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: releaseSigningProperties.getProperty(name)
}

val releaseKeyAlias = signingProperty("INCOMEDY_RELEASE_KEY_ALIAS")
    ?.takeIf { it.isNotBlank() }
    ?: "incomedy-release"
val releaseStorePassword = signingProperty("INCOMEDY_RELEASE_STORE_PASSWORD")
    ?.takeIf { it.isNotBlank() }
val releaseKeyPassword = signingProperty("INCOMEDY_RELEASE_KEY_PASSWORD")
    ?.takeIf { it.isNotBlank() }
val hasReleaseSigning = releaseKeystoreFile.isFile &&
    releaseStorePassword != null &&
    releaseKeyPassword != null
val requestedTasks = gradle.startParameter.taskNames
if (
    releaseKeystoreFile.isFile &&
    !hasReleaseSigning &&
    requestedTasks.any { it.contains("release", ignoreCase = true) }
) {
    throw GradleException(
        "Release keystore found at ${releaseKeystoreFile.path}, but signing credentials are missing. " +
            "Fill signing/android/release-signing.properties or provide INCOMEDY_RELEASE_* Gradle/env properties."
    )
}

android {
    namespace = "com.bam.incomedy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.bam.incomedy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(projects.shared)
    implementation(projects.feature.auth)

    debugImplementation(libs.compose.uiTooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.compose.uiTest.junit4)
    testImplementation(libs.robolectric)
}

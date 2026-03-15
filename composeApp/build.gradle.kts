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
// `local.properties` stays git-ignored and gives this repository a project-local place for Android SDK secrets.
val projectLocalPropertiesFile = rootProject.file("local.properties")
val projectLocalProperties = Properties().apply {
    if (projectLocalPropertiesFile.isFile) {
        projectLocalPropertiesFile.inputStream().use(::load)
    }
}

fun signingProperty(name: String): String? {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: releaseSigningProperties.getProperty(name)
}

/**
 * Runtime client configuration should prefer per-invocation env vars first, then ignored
 * project-local properties, and only then user/global Gradle properties.
 */
fun runtimeProperty(name: String): String? {
    return providers.environmentVariable(name).orNull
        ?: projectLocalProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
}

fun String.toBuildConfigString(): String {
    return buildString(length + 2) {
        append('"')
        this@toBuildConfigString.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(ch)
            }
        }
        append('"')
    }
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
val vkAndroidClientId = runtimeProperty("INCOMEDY_VK_ANDROID_CLIENT_ID")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
val vkAndroidClientSecret = runtimeProperty("INCOMEDY_VK_ANDROID_CLIENT_SECRET")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
val vkAndroidRedirectHost = runtimeProperty("INCOMEDY_VK_ANDROID_REDIRECT_HOST")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: "vk.ru"
val vkAndroidRedirectScheme = runtimeProperty("INCOMEDY_VK_ANDROID_REDIRECT_SCHEME")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: vkAndroidClientId?.let { "vk$it" }
    ?: "vkdisabled"
val vkIdScope = runtimeProperty("INCOMEDY_VK_ID_SCOPE")
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: "vkid.personal_info"
val hasVkAndroidSdkConfig = vkAndroidClientId != null && vkAndroidClientSecret != null
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
        manifestPlaceholders["VKIDClientID"] = vkAndroidClientId.orEmpty()
        manifestPlaceholders["VKIDClientSecret"] = vkAndroidClientSecret.orEmpty()
        manifestPlaceholders["VKIDRedirectHost"] = vkAndroidRedirectHost
        manifestPlaceholders["VKIDRedirectScheme"] = vkAndroidRedirectScheme
        buildConfigField("boolean", "VK_ANDROID_SDK_ENABLED", hasVkAndroidSdkConfig.toString())
        buildConfigField("String", "VK_ANDROID_CLIENT_ID", (vkAndroidClientId ?: "").toBuildConfigString())
        buildConfigField("String", "VK_ANDROID_REDIRECT_HOST", vkAndroidRedirectHost.toBuildConfigString())
        buildConfigField("String", "VK_ANDROID_REDIRECT_SCHEME", vkAndroidRedirectScheme.toBuildConfigString())
        buildConfigField("String", "VK_ID_SCOPE", vkIdScope.toBuildConfigString())
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
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
    implementation(libs.vkid)
    implementation(libs.vkid.onetap.compose)
    implementation(projects.shared)
    implementation(projects.feature.auth)

    debugImplementation(libs.compose.uiTooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.compose.uiTest.junit4)
    testImplementation(libs.robolectric)
}

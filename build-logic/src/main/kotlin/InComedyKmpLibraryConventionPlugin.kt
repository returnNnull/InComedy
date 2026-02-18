import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper

class InComedyKmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        plugins.withType<KotlinMultiplatformPluginWrapper> {
            extensions.getByType<KotlinMultiplatformExtension>().apply {
                iosArm64()
                iosSimulatorArm64()

                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
    }
    }
}

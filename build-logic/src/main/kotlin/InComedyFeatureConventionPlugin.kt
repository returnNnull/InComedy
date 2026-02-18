import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class InComedyFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
        pluginManager.apply("incomedy.kmp.library")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(project(":core:common"))
            }
        }
    }
    }
}

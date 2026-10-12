import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    id("com.android.kotlin.multiplatform.library") version "8.13.2" apply false
}

allprojects {
    group = "io.github.opencckmp"
    version = "0.1.0-SNAPSHOT"
}

val requestedTargets = providers.gradleProperty("opencc.targets").orNull?.split(',')?.toSet()
val allTargets = setOf("jvm", "android", "iosArm64", "iosSimulatorArm64", "iosX64",
    "macosArm64", "macosX64", "linuxX64", "mingwX64", "js", "wasmJs")
require(requestedTargets == null || allTargets.containsAll(requestedTargets)) {
    "Unknown opencc.targets: ${requestedTargets.orEmpty() - allTargets}"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "maven-publish")
    val isCli = name == "opencc-cli"
    fun enabled(target: String) = requestedTargets?.contains(target) ?: true
    if (!isCli && enabled("android")) apply(plugin = "com.android.kotlin.multiplatform.library")
    extensions.configure<KotlinMultiplatformExtension> {
        if (enabled("jvm")) jvm {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
        if (!isCli && enabled("android")) androidLibrary {
            namespace = "io.github.opencckmp." + project.name.removePrefix("opencc-").replace('-', '.')
            compileSdk = 36
            minSdk = 23
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            withHostTestBuilder {}.configure {}
        }
        if (enabled("iosArm64") && !isCli) iosArm64()
        if (enabled("iosSimulatorArm64") && !isCli) iosSimulatorArm64()
        if (enabled("iosX64") && !isCli) iosX64()
        if (enabled("macosArm64")) macosArm64()
        if (enabled("macosX64")) macosX64()
        if (enabled("linuxX64")) linuxX64()
        if (enabled("mingwX64")) mingwX64()
        if (!isCli && enabled("js")) js { browser(); nodejs() }
        if (!isCli && enabled("wasmJs")) wasmJs { browser(); nodejs() }
        applyDefaultHierarchyTemplate()
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
        if (isCli) targets.withType<KotlinNativeTarget>().configureEach {
            binaries.executable { entryPoint = "io.github.opencckmp.cli.main" }
        }
    }
    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set(project.name)
                description.set("Pure Kotlin Multiplatform port of OpenCC 1.4.2")
                licenses { license { name.set("Apache License 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
            }
        }
    }
}

tasks.register<Exec>("verifyUpstream") {
    commandLine("python3", "tools/upstream.py", "verify")
}
tasks.register<Exec>("fetchUpstream") {
    commandLine("python3", "tools/upstream.py", "fetch")
}

tasks.register<Exec>("prepareNativeProbes") {
    workingDir(rootDir)
    commandLine("python3", "tools/build_probes.py")
    mustRunAfter(":opencc-core:prepareConformance")
}


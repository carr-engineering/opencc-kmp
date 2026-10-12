import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    id("com.android.kotlin.multiplatform.library") version "8.13.2" apply false
    id("org.jetbrains.dokka") version "2.2.0"
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
    apply(plugin = "org.jetbrains.dokka")
    val isCli = name == "opencc-cli"
    fun enabled(target: String) = requestedTargets?.contains(target) ?: true
    if (!isCli && enabled("android")) apply(plugin = "com.android.kotlin.multiplatform.library")
    extensions.configure<KotlinMultiplatformExtension> {
        @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
        // Generated packs have no public declarations and live in ignored build directories.
        if (!project.name.startsWith("opencc-data-")) abiValidation()
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
        if (providers.gradleProperty("opencc.native.smallBinary").orElse("false").get().toBooleanStrict()) {
            targets.withType<KotlinNativeTarget>().configureEach {
                if (this is KotlinNativeTargetWithTests<*>) {
                    binaries.test(listOf(NativeBuildType.RELEASE))
                    testRuns.named("test") { setExecutionSourceFrom(binaries.getTest("", NativeBuildType.RELEASE)) }
                }
                binaries.all { binaryOption("smallBinary", "true") }
            }
        }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
        if (isCli) targets.withType<KotlinNativeTarget>().configureEach {
            for ((name, entry) in listOf("opencc" to "main", "opencc_dict" to "dictionaryMain", "opencc_phrase_extract" to "phraseMain")) {
                binaries.executable(name) { baseName = name; entryPoint = "io.github.opencckmp.cli.$entry" }
            }
        }
    }
    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "Consumer"
                url = rootProject.layout.projectDirectory.dir(".cache/maven").asFile.toURI()
            }
        }
        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set(project.name)
                description.set("Pure Kotlin Multiplatform port of OpenCC 1.4.2")
                licenses { license { name.set("Apache License 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
            }
        }
    }
    apply(from = rootProject.file("gradle/notices.gradle.kts"))
    if (name == "opencc-data" || name.startsWith("opencc-data-")) {
        val generateDataIdentity = tasks.register("generateDataIdentity") {
            val destination = layout.buildDirectory.file("generated/kotlin/DataIdentity.kt")
            outputs.file(destination)
            doLast {
                destination.get().asFile.apply {
                    parentFile.mkdirs()
                    writeText("package io.github.opencckmp.data.${project.name.replace('-', '_')}\ninternal const val upstreamDataVersion: String = \"1.4.2\"\n")
                }
            }
        }
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain { kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin")) }
            sourceSets.findByName("jvmMain")?.resources?.srcDir(layout.buildDirectory.dir("generated/resources"))
        }
        tasks.matching { it.name.startsWith("compile") || it.name.endsWith("SourcesJar") || it.name == "sourcesJar" }.configureEach {
            dependsOn(generateDataIdentity)
        }
        apply(from = rootProject.file("gradle/data-pack.gradle.kts"))
    }
}

dependencies {
    for (module in subprojects.filter { it.name != "opencc-data" && !it.name.startsWith("opencc-data-") && it.name != "opencc-all" }) {
        add("dokka", project(module.path))
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

tasks.register<Exec>("prepareDataReference") {
    workingDir(rootDir)
    commandLine("python3", "tools/build_oracle.py")
}


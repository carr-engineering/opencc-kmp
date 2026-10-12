import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { kotlin("multiplatform") version "2.4.0"; `maven-publish`; id("org.jetbrains.dokka") version "2.2.0" }
group = providers.gradleProperty("opencc.group").orElse("io.github.opencckmp").get()
version = providers.gradleProperty("opencc.version").orElse("0.1.0-SNAPSHOT").get()
val appleFramework = XCFramework("OpenCCInterop")
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
    jvm { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    js {
        nodejs(); browser()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions { moduleName.set("opencc-interop"); moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_ES) }
    }
    iosArm64(); iosSimulatorArm64(); iosX64(); macosArm64(); macosX64()
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework { baseName = "OpenCCInterop"; isStatic = true; appleFramework.add(this) }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies { implementation("${project.group}:opencc-core:${project.version}") }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
publishing { repositories { maven { name = "Consumer"; url = uri("../.cache/maven") } } }

val exportPackageManifest by tasks.registering {
    dependsOn("jsBrowserProductionLibraryDistribution")
    val source=layout.buildDirectory.file("dist/js/productionLibrary/package.json")
    val destination=layout.buildDirectory.file("export-package/package.json")
    inputs.file(source); outputs.file(destination)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val manifest=groovy.json.JsonSlurper().parse(source.get().asFile) as MutableMap<String,Any?>
        manifest.remove("devDependencies")
        destination.get().asFile.apply {
            parentFile.mkdirs(); writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifest))+"\n")
        }
    }
}
val bundleJavaScript by tasks.registering(Zip::class) {
    dependsOn(exportPackageManifest)
    archiveFileName.set("opencc-interop-${project.version}-js.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("dist/js/productionLibrary")) { exclude("*.map","package.json") }
    from(layout.buildDirectory.file("export-package/package.json"))
    from(file("../LICENSE"),file("../NOTICE"),file("../AUTHORS"),file("README.md"))
    from(listOf(file("../licenses/marisa.txt"),file("../licenses/rapidjson.txt"))) { into("licenses") }
    isPreserveFileTimestamps=false; isReproducibleFileOrder=true
}
val bundleAppleFramework by tasks.registering(Zip::class) {
    dependsOn("assembleOpenCCInteropReleaseXCFramework")
    archiveFileName.set("opencc-interop-${project.version}-apple.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("XCFrameworks/release"))
    from(file("../LICENSE"),file("../NOTICE"),file("../AUTHORS"),file("README.md"))
    from(listOf(file("../licenses/marisa.txt"),file("../licenses/rapidjson.txt"))) { into("licenses") }
    isPreserveFileTimestamps=false; isReproducibleFileOrder=true
}
publishing.publications.withType<MavenPublication>().configureEach {
    pom {
        name.set("OpenCC focused Swift and JavaScript exports")
        description.set("Opt-in host-language facade over the pure Kotlin Multiplatform OpenCC engine")
        licenses { license { name.set("Apache License 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
    }
    if (name=="kotlinMultiplatform") {
        artifact(bundleJavaScript) { classifier="javascript" }
        artifact(bundleAppleFramework) { classifier="apple" }
    }
}
if (providers.gradleProperty("opencc.release").orElse("false").get().toBooleanStrict()) {
    fun required(name:String)=providers.gradleProperty("opencc.$name").orNull?.takeIf { it.isNotBlank() }
        ?: error("Release staging requires -Popencc.$name")
    val repository=required("repositoryUrl").removeSuffix("/")
    val developerId=required("developerId"); val developerName=required("developerName")
    require(repository.startsWith("https://github.com/"))
    require(!project.version.toString().endsWith("-SNAPSHOT"))
    val documentation=tasks.register<Jar>("releaseDocumentationJar") {
        dependsOn("dokkaGenerate")
        archiveClassifier.set("javadoc")
        from(layout.buildDirectory.dir("dokka/html"))
        from(file("README.md")); from(file("src/commonMain")) { into("kdoc-source") }
        isPreserveFileTimestamps=false; isReproducibleFileOrder=true
    }
    publishing {
        repositories { maven { name="ReleaseStaging"; url=uri("../.cache/release/maven") } }
        publications.withType<MavenPublication>().configureEach {
            artifact(documentation)
            pom {
                url.set(repository)
                scm { url.set(repository); connection.set("scm:git:$repository.git"); developerConnection.set("scm:git:$repository.git"); tag.set("v${project.version}") }
                developers { developer { id.set(developerId); name.set(developerName) } }
            }
        }
    }
}

extra["opencc.sourceRoot"] = projectDir.parentFile
apply(from = file("../gradle/notices.gradle.kts"))

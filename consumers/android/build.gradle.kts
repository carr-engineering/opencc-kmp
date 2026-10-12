import java.security.MessageDigest

abstract class StageResources @javax.inject.Inject constructor(
    private val filesystem: org.gradle.api.file.FileSystemOperations,
    private val archives: org.gradle.api.file.ArchiveOperations,
) : DefaultTask() {
    @get:InputFiles abstract val bundles: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun unpack() {
        filesystem.sync {
            from(bundles.files.map { archives.zipTree(it) })
            into(outputDirectory)
        }
    }
}

plugins {
    id("com.android.application")
    kotlin("android")
}
val profile = providers.gradleProperty("opencc.profile").orElse("one").get()
val pack = when (profile) {
    "one" -> "opencc-data-s2t"
    "multi" -> "opencc-data-s2t-t2s"
    "full" -> "opencc-data"
    "baseline", "engine" -> null
    else -> error("Unknown profile $profile")
}
val dataResources by configurations.creating { isTransitive = false }
if (pack != null) dependencies.add(dataResources.name, "io.github.opencckmp:$pack:0.1.0-SNAPSHOT:resources@zip")
val stageResources by tasks.registering(StageResources::class) {
    bundles.from(dataResources)
    outputDirectory.set(layout.buildDirectory.dir("generated/assets"))
}
android {
    namespace = "io.github.opencckmp.consumer"
    compileSdk = 36
    defaultConfig { applicationId = "io.github.opencckmp.consumer"; minSdk = 23; targetSdk = 36; versionCode = 1; versionName = "1" }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets.named("main") {
        java.srcDir(if (pack == null) "src/$profile/kotlin" else "src/one/kotlin")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stageResources, StageResources::outputDirectory)
    }
}
dependencies {
    if (profile != "baseline") implementation("io.github.opencckmp:opencc-core:0.1.0-SNAPSHOT")
    if (pack != null) implementation("io.github.opencckmp:opencc-platform:0.1.0-SNAPSHOT")
}

tasks.register("reportDependencyDownloads") {
    doLast {
        val runtime = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        fun item(coordinate: String, file: File): Map<String, Any> = mapOf(
            "coordinate" to coordinate, "file" to file.name, "bytes" to file.length(),
            "sha256" to MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
        val records = runtime.map { item(it.moduleVersion.id.toString(), it.file) } +
            dataResources.files.map { item("io.github.opencckmp:$pack:0.1.0-SNAPSHOT:resources@zip", it) }
        layout.buildDirectory.file("reports/dependency-downloads.json").get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(records.sortedBy { it["coordinate"].toString() })))
        }
    }
}

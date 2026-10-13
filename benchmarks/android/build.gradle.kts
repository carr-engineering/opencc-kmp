import java.security.MessageDigest

abstract class StageBenchmarkResources @javax.inject.Inject constructor(
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
    id("com.android.application") version "8.13.2"
    kotlin("android") version "2.4.0"
}
val openccGroup = providers.gradleProperty("opencc.group").orElse("io.github.opencckmp").get()
val openccVersion = providers.gradleProperty("opencc.version").orElse("0.1.0-SNAPSHOT").get()
val dataResources by configurations.creating { isTransitive = false }
dependencies {
    implementation("$openccGroup:opencc-core:$openccVersion")
    implementation("$openccGroup:opencc-platform:$openccVersion")
    add(dataResources.name, "$openccGroup:opencc-data-s2t:$openccVersion:resources@zip")
}
val stageBenchmarkResources by tasks.registering(StageBenchmarkResources::class) {
    bundles.from(dataResources)
    outputDirectory.set(layout.buildDirectory.dir("generated/assets"))
}
android {
    namespace = "io.github.opencckmp.androidbenchmark"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.opencckmp.androidbenchmark"
        minSdk = 23; targetSdk = 36; versionCode = 1; versionName = "1"
        buildConfigField("String", "OPENCC_COORDINATES", "\"$openccGroup:*:$openccVersion\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stageBenchmarkResources, StageBenchmarkResources::outputDirectory)
    }
}
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }

tasks.register("reportBenchmarkDependencies") {
    doLast {
        fun record(coordinate: String, file: File): Map<String, Any> = mapOf(
            "coordinate" to coordinate, "file" to file.name, "bytes" to file.length(),
            "sha256" to MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) })
        val records = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .map { record(it.moduleVersion.id.toString(), it.file) } +
            dataResources.files.map { record("$openccGroup:opencc-data-s2t:$openccVersion:resources@zip", it) }
        layout.buildDirectory.file("reports/dependencies.json").get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(records.sortedBy { it["coordinate"].toString() })))
        }
    }
}

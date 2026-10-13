import java.security.MessageDigest
import groovy.json.JsonOutput

plugins { kotlin("multiplatform") version "2.4.0" }
val openccGroup = providers.gradleProperty("opencc.group").orElse("io.github.opencckmp").get()
val openccVersion = providers.gradleProperty("opencc.version").orElse("0.1.0-SNAPSHOT").get()
val resources by configurations.creating { isTransitive = false }
dependencies { add(resources.name, "$openccGroup:opencc-data-s2t:$openccVersion:resources@zip") }
val stageResources by tasks.registering(Sync::class) {
    from({ resources.map { zipTree(it) } })
    into(layout.buildDirectory.dir("generated/resources"))
}
kotlin {
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "benchmark.js"; sourceMaps = false } }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation("$openccGroup:opencc-core:$openccVersion")
            implementation("$openccGroup:opencc-platform:$openccVersion")
        }
        wasmJsMain { resources.srcDir(layout.buildDirectory.dir("generated/resources")) }
    }
}
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
tasks.matching { it.name.endsWith("ProcessResources") }.configureEach { dependsOn(stageResources) }

val benchmarkDependencyIdentity by tasks.registering {
    dependsOn(stageResources)
    outputs.upToDateWhen { false }
    doLast {
        fun record(coordinate: String, file: File): Map<String, Any> = mapOf(
            "coordinate" to coordinate, "file" to file.name, "bytes" to file.length(),
            "sha256" to MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) })
        val compilation = kotlin.targets.getByName("wasmJs").compilations.getByName("main")
        val configuration = configurations.getByName(compilation.compileDependencyConfigurationName)
        val records = configuration.resolvedConfiguration.resolvedArtifacts.map {
            record(it.moduleVersion.id.toString(), it.file)
        } + resources.files.map { record("$openccGroup:opencc-data-s2t:$openccVersion:resources@zip", it) }
        check(records.isNotEmpty())
        val identity = mapOf("group" to openccGroup, "version" to openccVersion,
            "configuration" to configuration.name, "artifacts" to records.sortedBy { it["coordinate"].toString() })
        layout.buildDirectory.file("reports/dependencies.json").get().asFile.apply {
            parentFile.mkdirs(); writeText(JsonOutput.prettyPrint(JsonOutput.toJson(identity)))
        }
    }
}

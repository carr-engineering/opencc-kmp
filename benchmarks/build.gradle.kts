import java.security.MessageDigest
import groovy.json.JsonOutput

plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.17"
}

// A standalone consumer: benchmark/JMH dependencies never enter published runtime graphs.
val openccGroup = providers.gradleProperty("opencc.group").orElse("io.github.opencckmp").get()
val openccVersion = providers.gradleProperty("opencc.version").orElse("0.1.0-SNAPSHOT").get()
kotlin {
    jvm()
    sourceSets.jvmMain.dependencies {
        implementation("$openccGroup:opencc-core:$openccVersion")
        implementation("$openccGroup:opencc-platform:$openccVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.17")
    }
}
benchmark {
    targets { register("jvm") }
    configurations {
        named("main") {
            warmups = 5
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
        register("smoke") {
            warmups = 1
            iterations = 2
            iterationTime = 300
            iterationTimeUnit = "ms"
            reportFormat = "json"
        }
    }
}

val benchmarkDependencyIdentity by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        val artifacts = configurations.getByName("jvmRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        val records = artifacts.sortedBy { it.moduleVersion.id.toString() }.map { artifact ->
            mapOf("coordinate" to artifact.moduleVersion.id.toString(), "file" to artifact.file.name,
                "sha256" to MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes())
                    .joinToString("") { "%02x".format(it) })
        }
        layout.buildDirectory.file("reports/benchmark-dependencies.json").get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(records)) + "\n")
        }
    }
}
tasks.matching { it.name.endsWith("Benchmark") }.configureEach { dependsOn(benchmarkDependencyIdentity) }

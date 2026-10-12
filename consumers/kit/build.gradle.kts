import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins { kotlin("multiplatform") }

val profile = providers.gradleProperty("opencc.profile").orElse("one").get()
val smallBinary = providers.gradleProperty("opencc.native.smallBinary").orElse("false").get().toBooleanStrict()
val buildProfile = profile + if (smallBinary) "-small" else ""
layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("build/profiles/$buildProfile/kit"))
val pack = when (profile) {
    "one" -> "opencc-data-s2t"
    "multi" -> "opencc-data-s2t-t2s"
    "full" -> "opencc-data"
    "engine", "baseline" -> null
    else -> error("Unknown opencc.profile $profile")
}
val resources by configurations.creating { isTransitive = false }
if (pack != null) dependencies.add(resources.name, "io.github.opencckmp:$pack:0.1.0-SNAPSHOT:resources@zip")
val stageResources by tasks.registering(Sync::class) {
    from({ resources.map { zipTree(it) } })
    into(layout.buildDirectory.dir("fixture.bundle"))
    doLast {
        destinationDir.resolve("Info.plist").writeText("""<?xml version="1.0"?><plist version="1.0"><dict><key>CFBundleIdentifier</key><string>io.github.opencckmp.fixture.resources</string><key>CFBundlePackageType</key><string>BNDL</string></dict></plist>""")
    }
}
val generateProfile by tasks.registering {
    inputs.property("profile", profile)
    val output = layout.buildDirectory.file("generated/kotlin/Profile.kt")
    outputs.file(output)
    doLast { output.get().asFile.apply { parentFile.mkdirs(); writeText("package io.github.opencckmp.consumer\ninternal const val fixtureProfile = \"$profile\"\n") } }
}
val generateFixtureHost by tasks.registering {
    val port = providers.gradleProperty("opencc.fixturePort").orElse("8765")
    inputs.property("port", port)
    val output = layout.buildDirectory.file("generatedTest/kotlin/FixtureHost.kt")
    outputs.file(output)
    doLast { output.get().asFile.apply {
        parentFile.mkdirs()
        writeText("package io.github.opencckmp.consumer\ninternal const val fixtureResourceUrl = \"http://127.0.0.1:${port.get()}/opencc\"\n")
    } }
}
kotlin {
    jvm()
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "ConsumerKit"
            isStatic = true
            if (smallBinary) binaryOption("smallBinary", "true")
        }
    }
    js { browser { commonWebpackConfig { outputFileName = "consumer.js"; sourceMaps = false }; testTask { useKarma { useChromeHeadless() } } }; nodejs { testTask { useMocha { timeout = "60s" } } }; binaries.executable() }
    wasmJs { browser { commonWebpackConfig { outputFileName = "consumer.js"; sourceMaps = false }; testTask { useKarma { useChromeHeadless() } } }; nodejs { testTask { useMocha { timeout = "60s" } } }; binaries.executable() }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            if (profile != "baseline") implementation("io.github.opencckmp:opencc-core:0.1.0-SNAPSHOT")
            if (pack != null) implementation("io.github.opencckmp:opencc-platform:0.1.0-SNAPSHOT")
        }
        commonMain {
            kotlin.srcDir(if (pack == null) "src/$profile/kotlin" else "src/data/kotlin")
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
        }
        for (target in targets.filter { it.name != "metadata" }) {
            target.compilations.getByName("main").defaultSourceSet.kotlin.srcDir(
                if (pack == null) "src/noDataHost/kotlin" else "src/dataHost/${target.name}/kotlin")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        commonTest { kotlin.srcDir(layout.buildDirectory.dir("generatedTest/kotlin")) }
        if (profile != "baseline") commonTest.dependencies { implementation("io.github.opencckmp:opencc-cache:0.1.0-SNAPSHOT") }
        if (profile != "baseline") commonTest.dependencies { implementation("io.github.opencckmp:opencc-jieba:0.1.0-SNAPSHOT") }
        if (profile != "baseline") commonTest { kotlin.srcDir("src/engineTest/kotlin") }
        commonMain { resources.srcDir(layout.buildDirectory.dir("fixture.bundle")) }
    }
}
tasks.matching { it.name.startsWith("compile") }.configureEach { dependsOn(generateProfile) }
tasks.matching { it.name.startsWith("compileTest") }.configureEach { dependsOn(generateFixtureHost) }
tasks.matching { it.name.endsWith("ProcessResources") }.configureEach { dependsOn(stageResources) }
tasks.withType<KotlinNativeTest>().configureEach {
    dependsOn(stageResources)
    environment("OPENCC_FIXTURE_BUNDLE", layout.buildDirectory.dir("fixture.bundle").get().asFile.absolutePath)
    environment("SIMCTL_CHILD_OPENCC_FIXTURE_BUNDLE", layout.buildDirectory.dir("fixture.bundle").get().asFile.absolutePath)
}
// HTTP resources are supplied by the verification runner, so test outputs cannot cache their state.
tasks.matching { it.name.endsWith("NodeTest") || it.name.endsWith("BrowserTest") }.configureEach {
    outputs.upToDateWhen { false }
}

tasks.register("verifyPublishedDependencies") {
    doLast {
        val selected = configurations.filter { it.isCanBeResolved && it.name in listOf("jvmCompileClasspath", "jvmRuntimeClasspath", resources.name) }
        for (configuration in selected) {
            val components = configuration.incoming.resolutionResult.allComponents
            check(components.none { it.id is org.gradle.api.artifacts.component.ProjectComponentIdentifier && it.id.displayName != "project :kit" })
        }
    }
}

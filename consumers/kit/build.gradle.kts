import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins { kotlin("multiplatform") }

val profile = providers.gradleProperty("opencc.profile").orElse("one").get()
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
kotlin {
    jvm()
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "ConsumerKit"
            isStatic = true
        }
    }
    js { browser { testTask { useKarma { useChromeHeadless() } } }; nodejs(); binaries.executable() }
    wasmJs { browser { testTask { useKarma { useChromeHeadless() } } }; nodejs(); binaries.executable() }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            if (profile != "baseline") implementation("io.github.opencckmp:opencc-core:0.1.0-SNAPSHOT")
            if (pack != null) implementation("io.github.opencckmp:opencc-platform:0.1.0-SNAPSHOT")
        }
        commonMain { kotlin.srcDir("src/$profile/kotlin") }
        commonTest.dependencies { implementation(kotlin("test")) }
        commonMain { resources.srcDir(layout.buildDirectory.dir("fixture.bundle")) }
    }
}
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

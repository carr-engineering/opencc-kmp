kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-core"))
    }
    sourceSets.findByName("jvmTest")?.dependencies { implementation(project(":opencc-platform")) }
}

tasks.register<Exec>("verifyDataBundles") {
    dependsOn("generateData")
    workingDir(rootDir)
    commandLine("python3", "tools/test_data.py")
}

val generateData by tasks.registering(Exec::class) {
    workingDir(rootDir)
    doFirst { layout.buildDirectory.file("generated/resources/opencc.zip").get().asFile.delete() }
    commandLine("python3", "tools/build_data.py", "--output", layout.buildDirectory.dir("generated/resources/opencc").get().asFile.absolutePath,
        "--archive", layout.buildDirectory.file("bundles/opencc-data.zip").get().asFile.absolutePath)
    inputs.file(rootProject.file("upstream.lock.json"))
    inputs.files(rootProject.fileTree("tools") { include("*.py") })
    inputs.files(rootProject.files("LICENSE", "NOTICE", "AUTHORS", "licenses/marisa.txt"))
    outputs.dir(layout.buildDirectory.dir("generated/resources/opencc"))
    outputs.file(layout.buildDirectory.file("bundles/opencc-data.zip"))
}
kotlin.sourceSets.commonMain { resources.srcDir(layout.buildDirectory.dir("generated/resources")) }
tasks.matching { it.name.endsWith("ProcessResources") || it.name.startsWith("process") && it.name.endsWith("Resources") }.configureEach {
    dependsOn(generateData)
}

tasks.register<Exec>("bundleSelectedData") {
    workingDir(rootDir)
    val configs = providers.gradleProperty("opencc.configs").orElse("")
    doFirst { if (configs.get().isBlank()) throw GradleException("Pass -Popencc.configs=s2t,t2s to select configurations") }
    commandLine("python3", "tools/build_data.py", "--configs", configs.get(),
        "--output", layout.buildDirectory.dir("selected/opencc").get().asFile.absolutePath)
}

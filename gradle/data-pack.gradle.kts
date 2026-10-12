val selectedConfigs = if (project.name == "opencc-data") emptyList() else project.name.removePrefix("opencc-data-").split('-')
val generateData by tasks.registering(Exec::class) {
    dependsOn(":prepareDataReference")
    workingDir(rootDir)
    val args = mutableListOf("python3", rootProject.file("tools/build_data.py").absolutePath,
        "--output", layout.buildDirectory.dir("generated/resources/opencc").get().asFile.absolutePath,
        "--archive", layout.buildDirectory.file("bundles/${project.name}.zip").get().asFile.absolutePath,
        "--oracle", rootProject.file(".cache/oracle").absolutePath)
    if (selectedConfigs.isNotEmpty()) args.addAll(listOf("--configs", selectedConfigs.joinToString(",")))
    commandLine(args)
    inputs.file(rootProject.file("upstream.lock.json"))
    inputs.files(rootProject.fileTree("tools") { include("*.py") })
    inputs.files(rootProject.files("LICENSE", "NOTICE", "AUTHORS", "licenses/marisa.txt", "licenses/darts-clone.txt"))
    inputs.property("configs", selectedConfigs)
    outputs.dir(layout.buildDirectory.dir("generated/resources/opencc"))
    outputs.file(layout.buildDirectory.file("bundles/${project.name}.zip"))
}
tasks.matching { it.name.endsWith("ProcessResources") || it.name.startsWith("process") && it.name.endsWith("Resources") }.configureEach {
    dependsOn(generateData)
}
extensions.configure<PublishingExtension> {
    publications.named<MavenPublication>("kotlinMultiplatform") {
        artifact(layout.buildDirectory.file("bundles/${project.name}.zip")) {
            classifier = "resources"
            extension = "zip"
            builtBy(generateData)
        }
    }
}

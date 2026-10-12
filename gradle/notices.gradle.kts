import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec

// Stage before publication metadata/signing: checksums must describe the shipped bytes. This also
// embeds notices in native/JS klibs, which do not consume JVM resource source-set packaging.
extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        val publication = this
        val capital = name.replaceFirstChar { it.uppercaseChar() }
        val stage = tasks.register<Exec>("embed${capital}Notices") {
            dependsOn(provider { publication.artifacts.flatMap { it.buildDependencies.getDependencies(null) } })
            val manifest = layout.buildDirectory.file("reports/notices/${publication.name}.json")
            inputs.files(rootProject.files("LICENSE", "NOTICE", "AUTHORS", "upstream.lock.json"))
            inputs.dir(rootProject.file("licenses"))
            inputs.file(rootProject.file("tools/embed_notices.py"))
            inputs.property("module", project.name)
            inputs.property("version", project.version.toString())
            // Compilers/JAR tasks can regenerate the artifact independently, so inspect every time.
            outputs.upToDateWhen { false }
            workingDir(rootDir)
            doFirst {
                manifest.get().asFile.apply {
                    parentFile.mkdirs()
                    writeText(publication.artifacts.joinToString(prefix = "[", postfix = "]") {
                        "\"" + it.file.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                    })
                }
                commandLine("python3", rootProject.file("tools/embed_notices.py"), "--module", project.name,
                    "--version", project.version.toString(), "--manifest", manifest.get().asFile)
            }
        }
        tasks.matching { it.name == "generateMetadataFileFor${capital}Publication" ||
            it.name == "sign${capital}Publication" || it.name.startsWith("publish${capital}PublicationTo") }.configureEach { dependsOn(stage) }
    }
}

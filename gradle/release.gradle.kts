import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

// Opt-in release staging is local and does not send credentials or artifacts to a registry.
if (providers.gradleProperty("opencc.release").orElse("false").get().toBooleanStrict()) {
    fun releaseProperty(name: String): String = providers.gradleProperty("opencc.$name").orNull
        ?.takeIf { it.isNotBlank() } ?: error("Release staging requires -Popencc.$name")
    val repositoryUrl = releaseProperty("repositoryUrl").removeSuffix("/")
    val developerId = releaseProperty("developerId")
    val developerName = releaseProperty("developerName")
    require(repositoryUrl.startsWith("https://github.com/")) { "Use the public GitHub repository URL" }
    require(!project.version.toString().endsWith("-SNAPSHOT")) { "Release staging rejects snapshots" }
    val releaseDocs = tasks.register<Jar>("releaseDocumentationJar") {
        dependsOn(rootProject.tasks.named("dokkaGenerate"))
        archiveClassifier.set("javadoc")
        from(rootProject.layout.buildDirectory.dir("dokka/html"))
        from(rootProject.file("docs")) { into("contract-guides") }
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    extensions.configure<PublishingExtension> {
        repositories { maven {
            name = "ReleaseStaging"
            url = rootProject.layout.projectDirectory.dir(".cache/release/maven").asFile.toURI()
        } }
        publications.withType<MavenPublication>().configureEach {
            artifact(releaseDocs)
            pom {
                url.set(repositoryUrl)
                scm {
                    url.set(repositoryUrl)
                    connection.set("scm:git:$repositoryUrl.git")
                    developerConnection.set("scm:git:$repositoryUrl.git")
                    tag.set("v${project.version}")
                }
                developers { developer { id.set(developerId); name.set(developerName) } }
            }
        }
    }
}

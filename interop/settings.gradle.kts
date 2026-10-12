pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri(providers.gradleProperty("opencc.repository").orElse("../.cache/maven").get()) } }
            filter { includeGroup(providers.gradleProperty("opencc.group").orElse("io.github.opencckmp").get()) }
        }
        mavenCentral()
    }
}
rootProject.name = "opencc-interop"

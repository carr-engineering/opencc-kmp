pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("opencc.repository").orElse("../.cache/maven").get()) }
        mavenCentral()
    }
}
rootProject.name = "opencc-benchmarks"

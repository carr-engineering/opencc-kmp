pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri("../.cache/maven") } }
            filter { includeGroup("io.github.opencckmp") }
        }
        google(); mavenCentral()
    }
}
rootProject.name = "opencc-published-consumers"
include("kit", "android")

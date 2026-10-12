pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "opencc-kmp"
include("opencc-core", "opencc-platform", "opencc-dictionary-tools", "opencc-data",
    "opencc-phrase-extract", "opencc-cli", "opencc-detect", "opencc-all")


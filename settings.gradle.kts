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
include("opencc-core", "opencc-platform", "opencc-cache", "opencc-jieba", "opencc-dictionary-tools", "opencc-data",
    "opencc-phrase-extract", "opencc-cli", "opencc-detect", "opencc-all")

// A pack coordinate encodes its sorted selection, so different contents never share a coordinate.
// Supply semicolon-separated selections, e.g. -Popencc.packs=s2t;t2s;s2t,t2s.
val packSelections = providers.gradleProperty("opencc.packs").orElse("s2t;s2t,t2s").get()
for (selection in packSelections.split(';').filter { it.isNotBlank() }) {
    val configs = selection.split(',').map { it.trim() }.distinct().sorted()
    require(configs.all { it.matches(Regex("[a-z0-9_]+")) }) { "Invalid pack configuration names: $selection" }
    val name = "opencc-data-" + configs.joinToString("-")
    include(name)
    project(":$name").projectDir = file(".cache/data-pack-projects/$name").apply { mkdirs() }
}


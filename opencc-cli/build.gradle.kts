kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-platform"))
        api(project(":opencc-dictionary-tools"))
        api(project(":opencc-data"))
        api(project(":opencc-phrase-extract"))
        api(project(":opencc-jieba"))
        api(project(":opencc-zip"))
    }
}


tasks.register("writeJvmClasspath") {
    dependsOn("jvmMainClasses")
    val destination = layout.buildDirectory.file("reports/jvm-classpath.txt")
    outputs.file(destination)
    doLast {
        val compilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
        destination.get().asFile.apply { parentFile.mkdirs(); writeText((compilation.output.allOutputs + compilation.runtimeDependencyFiles!!).asPath) }
    }
}

kotlin {
    sourceSets {
        for (target in targets) {
            if (target is org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget && target.name != "mingwX64") {
                val posix = maybeCreate("posixMain")
                posix.dependsOn(getByName("nativeMain"))
                target.compilations.getByName("main").defaultSourceSet.dependsOn(posix)
            }
        }
    }
}

// Executables are distributed separately from library modules. Every archive carries attribution
// and complete offline data; smaller library consumers remain independent of this tool graph.
val generateLaunchers by tasks.registering {
    val destination = layout.buildDirectory.dir("generated/launchers")
    outputs.dir(destination)
    doLast {
        val directory = destination.get().asFile.apply { mkdirs() }
        val launchers = mapOf("opencc" to "io.github.opencckmp.cli.MainKt", "opencc_dict" to "io.github.opencckmp.cli.DictionaryCommand", "opencc_phrase_extract" to "io.github.opencckmp.cli.PhraseCommand")
        for ((tool, entry) in launchers) {
            directory.resolve(tool).writeText("""#!/bin/sh
SCRIPT_DIR=${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")" && pwd)
exec java -Dopencc.launcher="${'$'}SCRIPT_DIR/$tool" -cp "${'$'}SCRIPT_DIR/../lib/*" $entry "${'$'}@"
""")
            directory.resolve("$tool.bat").writeText("@echo off\r\njava -Dopencc.launcher=\"%~dp0$tool.bat\" -cp \"%~dp0..\\lib\\*\" $entry %*\r\n")
        }
    }
}
tasks.register<Zip>("bundleJvmCli") {
    dependsOn("jvmJar", generateLaunchers, ":opencc-data:generateData")
    archiveFileName.set("opencc-kmp-${project.version}-jvm-cli.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(generateLaunchers) { into("bin"); filePermissions { unix("rwxr-xr-x") } }
    from(tasks.named("jvmJar")) { into("lib") }
    from({ configurations.getByName("jvmRuntimeClasspath").files }) { into("lib") }
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("AUTHORS"))
    from(rootProject.file("licenses")) { into("licenses") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
    val targetName = name
    val capitalTarget = targetName.replaceFirstChar { it.uppercaseChar() }
    tasks.register<Zip>("bundle${capitalTarget}Cli") {
        dependsOn(":opencc-data:generateData")
        archiveFileName.set("opencc-kmp-${project.version}-$targetName-cli.zip")
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        for (tool in listOf("opencc", "opencc_dict", "opencc_phrase_extract")) {
            val capitalTool = tool.replaceFirstChar { it.uppercaseChar() }
            dependsOn("link${capitalTool}ReleaseExecutable$capitalTarget")
            val extension = if (targetName == "mingwX64") "exe" else "kexe"
            from(layout.buildDirectory.file("bin/$targetName/${tool}ReleaseExecutable/$tool.$extension")) {
                into("bin"); rename { if (extension == "exe") "$tool.exe" else tool }; filePermissions { unix("rwxr-xr-x") }
            }
        }
        from(rootProject.file("opencc-data/build/generated/resources/opencc")) { into("share/opencc") }
        from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("AUTHORS"))
        from(rootProject.file("licenses")) { into("licenses") }
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

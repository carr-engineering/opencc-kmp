kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-core"))
    }
}

tasks.withType<Test>().configureEach {
    if (name == "jvmTest") exclude("**/Upstream*Test*")
}
val prepareProbes by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("python3", "tools/build_probes.py")
}
tasks.register<Test>("upstreamPhraseConformance") {
    dependsOn(prepareProbes, "jvmTestClasses")
    val standard = tasks.named<Test>("jvmTest")
    testClassesDirs = standard.get().testClassesDirs
    classpath = standard.get().classpath
    include("**/Upstream*Test*")
    systemProperty("opencc.projectRoot", rootDir.absolutePath)
}


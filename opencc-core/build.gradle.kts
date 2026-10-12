// Runtime uses only the Kotlin standard library.

val prepareConformance by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("python3", "tools/prepare_conformance.py")
}
tasks.withType<Test>().configureEach {
    if (name == "jvmTest") exclude("**/UpstreamConformanceTest*")
}
tasks.register<Test>("upstreamConformance") {
    dependsOn(prepareConformance, "jvmTestClasses")
    val standard = tasks.named<Test>("jvmTest")
    testClassesDirs = standard.get().testClassesDirs
    classpath = standard.get().classpath
    include("**/UpstreamConformanceTest*")
    systemProperty("opencc.projectRoot", rootDir.absolutePath)
}


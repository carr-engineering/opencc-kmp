// Runtime uses only the Kotlin standard library.

val prepareConformance by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("python3", "tools/prepare_conformance.py")
}
tasks.withType<Test>().configureEach {
    if (name == "jvmTest") exclude("**/Upstream*Test*")
}
tasks.register<Test>("upstreamConformance") {
    dependsOn(prepareConformance, ":prepareNativeProbes", "jvmTestClasses")
    val standard = tasks.named<Test>("jvmTest")
    testClassesDirs = standard.get().testClassesDirs
    classpath = standard.get().classpath
    include("**/Upstream*Test*")
    systemProperty("opencc.projectRoot", rootDir.absolutePath)
}

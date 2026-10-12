kotlin { sourceSets.commonMain.dependencies { api(project(":opencc-core")) } }

tasks.withType<Test>().configureEach {
    if (name == "jvmTest") exclude("**/Upstream*Test*")
}
tasks.register<Test>("upstreamJiebaConformance") {
    dependsOn(":prepareNativeProbes", "prepareJiebaConformance", "jvmTestClasses")
    val standard = tasks.named<Test>("jvmTest")
    testClassesDirs = standard.get().testClassesDirs
    classpath = standard.get().classpath
    include("**/Upstream*Test*")
    systemProperty("opencc.projectRoot", rootDir.absolutePath)
    outputs.upToDateWhen { false }
}
tasks.register<Exec>("prepareJiebaConformance") {
    dependsOn(":prepareNativeProbes")
    workingDir(rootDir)
    commandLine("python3", "tools/prepare_jieba.py", "--probes-ready")
}

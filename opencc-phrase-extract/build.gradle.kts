kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-core"))
    }
}

tasks.withType<Test>().configureEach {
    if (name == "jvmTest") exclude("**/Upstream*Test*")
}
tasks.register<Test>("upstreamPhraseConformance") {
    dependsOn(":prepareNativeProbes", "jvmTestClasses")
    val standard = tasks.named<Test>("jvmTest")
    testClassesDirs = standard.get().testClassesDirs
    classpath = standard.get().classpath
    include("**/Upstream*Test*")
    systemProperty("opencc.projectRoot", rootDir.absolutePath)
}


kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-core"))
    }
}

tasks.withType<Test>().configureEach {
    if (name == "jvmTest") exclude("**/UpstreamWriterTest*")
}
tasks.register<Test>("upstreamWriterConformance") {
    dependsOn(":opencc-core:prepareConformance", "jvmTestClasses")
    val standard = tasks.named<Test>("jvmTest")
    testClassesDirs = standard.get().testClassesDirs
    classpath = standard.get().classpath
    include("**/UpstreamWriterTest*")
    systemProperty("opencc.projectRoot", rootDir.absolutePath)
}

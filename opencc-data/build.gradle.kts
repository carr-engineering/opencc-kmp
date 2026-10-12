kotlin {
    sourceSets.findByName("jvmTest")?.dependencies { implementation(project(":opencc-platform")) }
}

tasks.register<Exec>("verifyDataBundles") {
    dependsOn("generateData")
    workingDir(rootDir)
    commandLine("python3", "tools/test_data.py")
}

tasks.register<Exec>("bundleSelectedData") {
    workingDir(rootDir)
    val configs = providers.gradleProperty("opencc.configs").orElse("")
    doFirst { if (configs.get().isBlank()) throw GradleException("Pass -Popencc.configs=s2t,t2s to select configurations") }
    commandLine("python3", "tools/build_data.py", "--configs", configs.get(),
        "--output", layout.buildDirectory.dir("selected/opencc").get().asFile.absolutePath)
}

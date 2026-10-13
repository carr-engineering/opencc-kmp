kotlin {
    sourceSets.findByName("jvmTest")?.dependencies { implementation(project(":opencc-zip")) }
    sourceSets.commonMain.dependencies {
        api(project(":opencc-core"))
    }
    sourceSets {
        if (findByName("jvmMain") != null || findByName("androidMain") != null) {
            val shared = maybeCreate("jvmAndroidMain")
            shared.dependsOn(commonMain.get())
            findByName("jvmMain")?.dependsOn(shared)
            findByName("androidMain")?.dependsOn(shared)
        }
        for (target in targets) {
            if (target is org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget && target.name != "mingwX64") {
                val platformMain = maybeCreate("posixMain")
                platformMain.dependsOn(commonMain.get())
                target.compilations.getByName("main").defaultSourceSet.dependsOn(platformMain)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    if (name == "jvmTest") exclude("**/Upstream*Test*")
}
tasks.register<Test>("resourceConformance") {
    dependsOn(":prepareNativeProbes", "jvmTestClasses")
    val standard = tasks.named<Test>("jvmTest")
    testClassesDirs = standard.get().testClassesDirs
    classpath = standard.get().classpath
    include("**/UpstreamResourceTest*")
    include("**/UpstreamFileConfigTest*")
    systemProperty("opencc.projectRoot", rootDir.absolutePath)
    outputs.upToDateWhen { false }
}

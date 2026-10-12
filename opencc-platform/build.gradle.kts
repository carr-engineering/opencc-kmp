kotlin {
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

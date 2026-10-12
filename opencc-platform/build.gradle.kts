kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-core"))
    }
    sourceSets {
        findByName("androidMain")?.kotlin?.srcDir("src/jvmMain/kotlin")
        for (target in targets) {
            if (target is org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget && target.name != "mingwX64") {
                val platformMain = maybeCreate("posixMain")
                platformMain.dependsOn(commonMain.get())
                target.compilations.getByName("main").defaultSourceSet.dependsOn(platformMain)
            }
        }
    }
}

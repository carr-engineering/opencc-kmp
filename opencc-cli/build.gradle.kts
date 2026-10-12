kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-platform"))
        api(project(":opencc-dictionary-tools"))
        api(project(":opencc-data"))
        api(project(":opencc-phrase-extract"))
    }
}


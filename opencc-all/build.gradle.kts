kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":opencc-core"))
        api(project(":opencc-platform"))
        api(project(":opencc-cache"))
        api(project(":opencc-jieba"))
        api(project(":opencc-zip"))
        api(project(":opencc-dictionary-tools"))
        api(project(":opencc-data"))
        api(project(":opencc-phrase-extract"))
    }
}


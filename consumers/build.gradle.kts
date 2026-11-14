plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("android") version "2.4.21" apply false
    id("com.android.application") version "8.13.2" apply false
}

allprojects {
    configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
}

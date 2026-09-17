plugins {
    id("com.android.application") version "8.3.2" apply false
    id("com.android.library") version "8.3.2" apply false
    kotlin("android") version "1.8.22" apply false
    kotlin("plugin.serialization") version "1.8.22" apply false
    id("com.google.devtools.ksp") version "1.8.22-1.0.11" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

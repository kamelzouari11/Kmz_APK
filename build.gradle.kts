plugins {
    id("com.android.application") version "8.7.2" apply false
    kotlin("android") version "1.9.25" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

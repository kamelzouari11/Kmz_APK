plugins { id("com.android.application") }
android {
    namespace = "tn.kmzapk.mesplacements.reports"
    compileSdk = 35
    defaultConfig {
        applicationId = "tn.kmzapk.mesplacements.reports"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}
// Regenerate shared HTML/JS/CSS assets manually from the main project when the
// web or PDF code changes. Android Studio can build from the checked-in assets
// without depending on the user's shell PATH or NVM installation.
val prepareReportsWeb by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir.parentFile)
    commandLine("npm", "run", "prepare:android-reports")
    inputs.dir(rootProject.projectDir.resolve("web"))
    inputs.file(rootProject.projectDir.resolve("vite.config.js"))
    inputs.dir(rootProject.projectDir.parentFile.resolve("src"))
    inputs.file(rootProject.projectDir.parentFile.resolve("package-lock.json"))
    outputs.dir(projectDir.resolve("src/main/assets"))
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Lire le local.properties COMMUN à la racine de KmzAPK (partagé par toutes les apps)
// Chemin: app/ -> SimpleIPTV/ -> KmzAPK/local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.rootDir.parentFile.resolve("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val githubToken: String = localProperties.getProperty("github.token", "")

android {
    namespace = "com.example.simpleiptv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.simpleiptv"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        // Token GitHub injecté depuis local.properties commun (jamais dans le code)
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true   // nécessaire pour BuildConfig.GITHUB_TOKEN
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    lint {
        abortOnError = false
    }
}


dependencies {
    // Retrofit + Moshi
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.foundation:foundation")

    // Material3
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-session:1.2.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

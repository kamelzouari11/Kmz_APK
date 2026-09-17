plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.football.footballapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.football.footballapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "0.2"
        buildConfigField(
            "String",
            "FOOTBALL_DATA_API_KEY",
            "\"${project.findProperty("FOOTBALL_DATA_API_KEY") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "API_FOOTBALL_KEY",
            "\"${project.findProperty("API_FOOTBALL_KEY") ?: ""}\"",
        )
        buildConfigField(
            "boolean",
            "API_FOOTBALL_USE_RAPID",
            project.findProperty("API_FOOTBALL_USE_RAPID")?.toString() ?: "false",
        )
        buildConfigField(
            "String",
            "TV_SERVER_URL",
            "\"${providers.environmentVariable("FOOTBALL_TV_SERVER_URL_OVERRIDE").orNull ?: project.findProperty("TV_SERVER_URL") ?: "http://10.0.2.2:10000"}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("com.google.android.material:material:1.14.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-moshi:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    implementation("com.squareup.moshi:moshi:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

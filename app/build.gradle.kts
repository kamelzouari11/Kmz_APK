plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.football.footballapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.football.footballapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
        buildConfigField(
            "String",
            "FOOTBALL_DATA_API_KEY",
            "\"${project.findProperty("FOOTBALL_DATA_API_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "API_FOOTBALL_KEY",
            "\"${project.findProperty("API_FOOTBALL_KEY") ?: ""}\""
        )
        buildConfigField(
            "boolean",
            "API_FOOTBALL_USE_RAPID",
            "${project.findProperty("API_FOOTBALL_USE_RAPID") ?: "false"}"
        )
        buildConfigField(
            "String",
            "TV_SERVER_URL",
            "\"${project.findProperty("TV_SERVER_URL") ?: "http://10.0.2.2:10000"}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("com.google.android.material:material:1.11.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0") // reflection-based adapter

    debugImplementation("androidx.compose.ui:ui-tooling")
}

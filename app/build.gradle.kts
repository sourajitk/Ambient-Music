plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.sourajitk.ambient_music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sourajitk.ambient_music"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.6-alpha"
    }
    signingConfigs {
        val hasSigningEnv = System.getenv("SIGNING_KEYSTORE_PASSWORD") != null

        if (hasSigningEnv) {
            create("release") {
                storeFile = rootProject.file("release-keystore.jks")
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release").apply {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
}

spotless {
    kotlin {
        ktfmt().googleStyle()
        // ktfmt().dropboxStyle()

        // Set the files to format
        target("src/**/*.kt")
        targetExclude("build/**/*.kt")
        // targetExclude("src/main/kotlin/com/example/dontformat/*")
    }
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.coil)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
}
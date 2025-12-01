import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.spotless)
}

fun execCommand(command: String): String? {
    val cmd = command.split(" ").toTypedArray()
    val process = ProcessBuilder(*cmd)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    return process.inputStream.bufferedReader().readLine()?.trim()
}

val commitCount by project.extra {
    execCommand("git rev-list --count HEAD")?.toInt()
        ?: throw GradleException("Unable to get number of commits. Make sure git is initialized.")
}

val commitHash by project.extra {
    execCommand("git rev-parse --short HEAD")
        ?: throw GradleException(
            "Unable to get commit hash. Make sure git is initialized."
        )
}

android {
    namespace = "com.sourajitk.ambient_music"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.sourajitk.ambient_music"
        minSdk = 31
        compileSdk {
            version = release(36) {
                minorApiLevel = 1
            }
        }
        versionCode = commitCount
        val isDogfoodBuild = System.getenv("GITHUB_REF_NAME") == "dogfood"
        versionName = if (isDogfoodBuild) {
            "3.3.2-dogfood-$commitHash"
        } else {
            "3.3.2-$commitHash"
        }
        resValue("string", "app_version", "\"${versionName}\"")
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
        getByName("debug") {
            signingConfig = signingConfigs.findByName("release")
        }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    androidResources {
        generateLocaleConfig = true
    }
}

spotless {
    kotlin {
        ktlint("0.50.0").editorConfigOverride(
            mapOf(
                "ktlint_standard_package-name" to "disabled",
                "indent_size" to "4",
                "continuation_indent_size" to "4"
            )
        )

        target("src/**/*.kt")
        targetExclude("build/**/*.kt")
    }
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.ui)
    implementation(libs.app.update.ktx)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.fragment.ktx)
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.material3.window.size.class1)
    implementation(libs.material3.window.size.class1)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.foundation)
}

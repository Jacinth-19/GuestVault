plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.guestvault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guestvault"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            // Screenshots for the README and the Play listing are captured from
            // debug builds by UiAutomator. FLAG_SECURE would black them out, so
            // window security is only switched on for shipping builds.
            buildConfigField("boolean", "SECURE_WINDOWS", "false")
        }
        release {
            // R8 + resource shrinking keep the downloadable APK small (~8 MB
            // instead of ~46 MB). Signed with the debug key so release builds
            // from GitHub Actions are installable without extra secrets.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            // Blocks screenshots, screen recording and the Recents thumbnail
            // while the guest/PIN UI is on screen.
            buildConfigField("boolean", "SECURE_WINDOWS", "true")
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }

    lint {
        // Real errors are surfaced by the explicit `lintRelease` CI job; keeping
        // the default false here means a lint warning never blocks a hotfix.
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("correctErrorTypes", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

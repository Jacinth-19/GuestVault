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
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Google Play upload key.
    //
    // The keystore itself is never committed: CI decodes it from the
    // UPLOAD_KEYSTORE_BASE64 secret into $RUNNER_TEMP and exports the four
    // variables below. When they are absent (a fresh clone, a contributor's
    // machine, or a repository that has not been set up yet) the release build
    // falls back to the debug key — which Play rejects — so the release
    // workflow refuses to publish an .aab unless it is release-signed.
    signingConfigs {
        create("upload") {
            val keystorePath = System.getenv("UPLOAD_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
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
            // instead of ~46 MB).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release-signed when the upload key is configured, debug-signed
            // otherwise so sideloadable builds never depend on secrets.
            signingConfig = if (System.getenv("UPLOAD_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
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
    // Onboarding animations. Kept in res/raw as generated JSON so they are
    // reviewed like code; the screens fall back to static art if a file ever
    // fails to parse.
    implementation(libs.lottie.compose)
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

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val useFirebaseEmulators = providers.gradleProperty("useFirebaseEmulators")
    .orNull
    .toBoolean()

android {
    namespace = "com.example.loginfirebase"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.loginfirebase"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useFirebaseEmulators.toString())
            manifestPlaceholders["usesCleartextTraffic"] = useFirebaseEmulators.toString()
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            optimization {
                enable = false
            }
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.material)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Firebase (BoM controla as versões compatíveis entre si)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(libs.maplibre.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

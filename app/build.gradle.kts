import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // Round 27 — Firebase Cloud Messaging. The plugin reads
    // `app/google-services.json` to generate FCM resources at build
    // time. The file currently contains a PLACEHOLDER config (fake
    // project_id, no real API key) — FCMService.kt stays dormant on
    // device. To activate FCM: replace this file with the one
    // downloaded from Firebase Console and rebuild.
    alias(libs.plugins.google.services)
    alias(libs.plugins.cyclonedx)
}

android {
    namespace = "com.monitorung.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.monitorung.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 34
        versionName = "0.32.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ARM phones plus x86_64 emulators and ChromeOS.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // APK splits: separate APK per ABI for smaller download size.
    // Universal APK kept as fallback for distribution flexibility.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Read signing config from gradle.properties (local) or env vars (CI/CD)
            val keystorePath = providers.gradleProperty("monitorung.keystore.path").orNull
            val keystorePw = providers.gradleProperty("monitorung.keystore.password").orNull
            val keyAliasName = providers.gradleProperty("monitorung.key.alias").orNull
            val keyPw = providers.gradleProperty("monitorung.key.password").orNull
            if (keystorePath != null && keystorePw != null && keyAliasName != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystorePath)
                    storePassword = keystorePw
                    keyAlias = keyAliasName
                    keyPassword = keyPw
                }
            } else {
                logger.warn("⚠️  Release signing not configured — set keystore properties in gradle.properties or CI env vars")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // Product flavors: dev (no pinning, localhost) vs prod (pinning enabled, production URL)
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
            buildConfigField("boolean", "CERTIFICATE_PINNING_ENABLED", "false")
            // Wave 4: MQTT auth. Local mosquitto (no auth required for dev
            // convenience) — empty password keeps the optional paho call a no-op
            // when the broker doesn't enforce auth.
            buildConfigField("String", "MQTT_USERNAME", "\"\"")
            buildConfigField("String", "MQTT_PASSWORD", "\"\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://monitorterop.my.id\"")
            buildConfigField("boolean", "CERTIFICATE_PINNING_ENABLED", "true")
            // Round 29: MQTT auth baked into APK. Same shared secret as server.
            // MUST be passed via -PmqttPassword=... on the gradle command line or
            // via local.properties — no default fallback (Round 29 audit P0 #1).
            // Without this property, the prod build fails loudly instead of
            // shipping a known password (`afe55cfd...`) extractable from
            // the APK via `apktool` or `strings`. Rotation procedure lives
            // in `server/mosquitto/README.md`.
            val mqttUser = (project.findProperty("mqttUsername") as String?) ?: "monitorung"
            val mqttPass = (project.findProperty("mqttPassword") as String?)
            if (mqttPass.isNullOrEmpty()) {
                throw GradleException(
                    "MQTT_PASSWORD is required for prod build. Pass via -PmqttPassword=<secret> " +
                    "on the gradle command line, or set `mqttPassword=...` in local.properties. " +
                    "See server/mosquitto/README.md for the rotation procedure."
                )
            }
            buildConfigField("String", "MQTT_USERNAME", "\"$mqttUser\"")
            buildConfigField("String", "MQTT_PASSWORD", "\"$mqttPass\"")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Don't strip native libs — they handle their own strip on debug builds
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // Performance: enable R8 full mode for smaller, faster code
    androidResources {
        // Generate density-specific resources at build time
        noCompress += listOf("kotlin", "png", "webp")
    }
}

dependencies {
    // Compose BOM — pins all Compose artifact versions.
    implementation(platform(libs.compose.bom))

    // Compose
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Window size class for responsive layouts (phone/tablet/foldable)
    implementation(libs.compose.material3.window.size)

    // Core Android
    implementation(libs.bundles.android.runtime)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    // Note: libs.hilt.compiler (line 139, Dagger Hilt) and
    // libs.androidx.hilt.compiler (below, Hilt-Work) are intentionally different
    // processors — the Hilt-Work processor generates *_HiltWorker_AssistedFactory_Impl
    // classes required by HiltWorkerFactory.createWorker().
    ksp(libs.androidx.hilt.compiler)

    // Persistence: Room + SQLCipher + DataStore
    implementation(libs.bundles.persistence)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.bundles.http)

    // MQTT - Eclipse Paho
    implementation(libs.paho.mqtt)

    // Round 27 — Firebase Cloud Messaging (FCM) for OS-level push.
    // The firebase-messaging artifact is shipped via Google Play
    // Services on most devices, so the on-device APK size impact
    // is small. The FirebaseMessagingService (FCMService.kt) is
    // wired in code but stays dormant until `google-services.json`
    // is present in `app/`.
    implementation(libs.firebase.messaging)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)

    // Security: encrypted storage + Tink
    implementation(libs.bundles.security)

    // Google Play Services - Location
    implementation(libs.play.services.location)

    // Coroutines trio (core + android + play-services)
    implementation(libs.bundles.coroutines)

    // Accompanist (permissions)
    implementation(libs.accompanist.permissions)

    // Biometric authentication — required to access app after auto-login
    implementation(libs.androidx.biometric)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

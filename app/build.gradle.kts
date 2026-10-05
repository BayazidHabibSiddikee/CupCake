plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.cupcake"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cupcake"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++17")
                cppFlags.add("-frtti")
                cppFlags.add("-fexceptions")
                cppFlags.add("-DLLAMA_LOG_LEVEL=2")
                arguments.add("-DANDROID_STL=c++_shared")
                arguments.add("-DANDROID_ARM_NEON=TRUE")
            }
        }

        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            // Real keystore via env or gradle.properties:
            //   CUPCAKE_KEYSTORE_PATH / cupcake.keystore.path, etc.
            // Falls back to the debug keystore so local release builds work
            // without secrets. Do NOT ship production builds signed this way.
            val ksPath = System.getenv("CUPCAKE_KEYSTORE_PATH")
                ?: (project.findProperty("cupcake.keystore.path") as String?).orEmpty()
            if (ksPath.isNotBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("CUPCAKE_KEYSTORE_PASSWORD")
                    ?: (project.findProperty("cupcake.keystore.password") as String?)
                keyAlias = System.getenv("CUPCAKE_KEY_ALIAS")
                    ?: (project.findProperty("cupcake.key.alias") as String?)
                keyPassword = System.getenv("CUPCAKE_KEY_PASSWORD")
                    ?: (project.findProperty("cupcake.key.password") as String?)
            } else {
                val debugKs = signingConfigs.getByName("debug")
                storeFile = debugKs.storeFile
                storePassword = debugKs.storePassword
                keyAlias = debugKs.keyAlias
                keyPassword = debugKs.keyPassword
            }
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
            signingConfig = signingConfigs.getByName("release")
            matchingFallbacks += "debug"
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=androidx.lifecycle.ExperimentalLifecycleApi"
        )
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packagingOptions {
        resources.excludes.add("META-INF/*")
        jniLibs.pickFirsts.add("libc++_shared.so")
        jniLibs.pickFirsts.add("libllama_jni.so")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val coreSplashScreen = libs.androidx.core.splashscreen.get()
    val activityCompose = libs.androidx.activity.compose.get()
    val lifecycleViewmodelCompose = libs.androidx.lifecycle.viewmodel.compose.get()
    val lifecycleRuntimeCompose = libs.androidx.lifecycle.runtime.compose.get()
    val composeUi = libs.androidx.compose.ui.main.get()
    val composeFoundation = libs.androidx.compose.foundation.main.get()
    val composeFoundationText = libs.androidx.compose.foundation.text.get()
    val composeAnimation = libs.androidx.compose.animation.get()
    val composeRuntime = libs.androidx.compose.runtime.get()
    val materialIconsCore = libs.androidx.compose.material.icons.core.get()
    val composeBom = libs.androidx.compose.bom.get()
    val material3 = libs.androidx.compose.material3.get()
    val materialIcons = libs.androidx.compose.material.icons.extended.get()
    val navigationCompose = libs.androidx.navigation.compose.get()
    val hilt = libs.hilt.android.get()
    val hiltCompiler = libs.hilt.compiler.get()
    val room = libs.androidx.room.runtime.get()
    val roomKtx = libs.androidx.room.ktx.get()
    val roomCompiler = libs.androidx.room.compiler.get()
    val datastore = libs.androidx.datastore.preferences.get()
    val coroutines = libs.kotlinx.coroutines.android.get()
    val flow = libs.kotlinx.coroutines.core.get()
    val serialization = libs.kotlinx.serialization.json.get()
    val coil = libs.coil.compose.get()
    val retrofit = libs.retrofit.retrofit.get()
    val retrofitScalars = libs.retrofit.converter.scalars.get()
    val okhttp = libs.okhttp.okhttp.get()
    val guava = libs.guava.get()
    val mpAndroidChart = libs.github.mpandroidchart.get()
    val accompanistPermissions = libs.accompanist.permissions.get()
    val accompanistSystemUi = libs.accompanist.systemuicontroller.get()
    val mockk = libs.mockk.get()
    val junit = libs.junit.get()
    val espresso = libs.androidx.espresso.core.get()
    val composeTest = libs.androidx.compose.ui.test.manifest.get()
    val composeTestJunit4 = libs.androidx.compose.ui.test.junit4.get()
    val robolectric = libs.robolectric.get()
    val truth = libs.truth.get()
    val turbine = libs.turbine.get()

    // Ktor for WebSocket server
    val ktorVersion = "2.3.9"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation(coreSplashScreen)
    implementation(activityCompose)
    implementation(lifecycleViewmodelCompose)
    implementation(lifecycleRuntimeCompose)

    implementation(platform(composeBom))
    implementation(composeUi)
    implementation(composeFoundation)
    implementation(composeFoundationText)
    implementation(composeAnimation)
    implementation(composeRuntime)
    implementation(material3)
    implementation(materialIconsCore)
    implementation(materialIcons)
    implementation(navigationCompose)

    implementation(hilt)
    kapt(hiltCompiler)

    implementation(room)
    implementation(roomKtx)
    kapt(roomCompiler)

    implementation(datastore)

    implementation(coroutines)
    implementation(flow)

    implementation(serialization)

    implementation(coil)

    implementation(retrofit)
    implementation(retrofitScalars)
    implementation(okhttp)
    implementation(guava)

    implementation(mpAndroidChart)

    implementation(accompanistPermissions)
    implementation(accompanistSystemUi)

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Play Billing for Pro upgrade
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    testImplementation(junit)
    testImplementation(mockk)
    testImplementation(robolectric)
    testImplementation(truth)
    testImplementation(turbine)
    androidTestImplementation(espresso)
    androidTestImplementation(composeTest)
    androidTestImplementation(composeTestJunit4)
    androidTestImplementation("androidx.test:rules:1.6.0")
    androidTestImplementation("androidx.test:runner:1.6.0")
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")

    // NOTE: androidx.bluetooth removed - artifacts do not exist at the pinned
    // version and no code imports them yet (ble/ package is empty).
    // Re-add alongside the BLE GATT client implementation.
}

kapt {
    correctErrorTypes = true
    javacOptions {
        option("-Xlint:unchecked")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=androidx.lifecycle.ExperimentalLifecycleApi"
        )
    }
}

tasks.withType<com.android.build.gradle.internal.tasks.StripDebugSymbolsTask> {
    isEnabled = false
}
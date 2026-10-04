plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
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
                cppFlags.add("-DQWEN_LOG_LEVEL=2")
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            matchingFallbacks = ["debug"]
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            matchingFallbacks = ["release"]
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
        jniLibs.pickFirsts.add("libqwen.so")
    }

    externalNativeBuild {
        cmake {
            path = "src/main/cpp/CMakeLists.txt"
            version = "3.22.1"
        }
    }
}

dependencies {
    val coreSplashScreen = libs.androidx.core.splashscreen.get()
    val activityCompose = libs.androidx.activity.compose.get()
    val lifecycleViewmodelCompose = libs.androidx.lifecycle.viewmodelCompose.get()
    val composeBom = libs.androidx.compose.bom.get()
    val material3 = libs.androidx.compose.material3.get()
    val materialIcons = libs.androidx.compose.materialIconsExtended.get()
    val navigationCompose = libs.androidx.navigation.compose.get()
    val hilt = libs.hilt.android.get()
    val hiltCompiler = libs.hilt.compiler.get()
    val room = libs.androidx.room.runtime.get()
    val roomKtx = libs.androidx.room.ktx.get()
    val roomCompiler = libs.androidx.room.compiler.get()
    val datastore = libs.androidx.datastore.preferences.get()
    val coroutines = libs.kotlinx.coroutines.android.get()
    val flow = libs.kotlinx.coroutines.flow.get()
    val serialization = libs.kotlinx.serialization.json.get()
    val coil = libs.coil.compose.get()
    val mpAndroidChart = libs.github.mpandroidchart.get()
    val accompanistPermissions = libs.accompanist.permissions.get()
    val accompanistSystemUi = libs.accompanist.systemuicontroller.get()
    val mockk = libs.mockk.get()
    val junit = libs.junit.get()
    val espresso = libs.androidx.espresso.core.get()
    val composeTest = libs.androidx.compose.uiTestManifest.get()
    val composeTestJunit4 = libs.androidx.compose.uiTestJunit4.get()
    val robolectric = libs.robolectric.get()
    val truth = libs.truth.get()
    val turbine = libs.turbine.get()

    implementation(coreSplashScreen)
    implementation(activityCompose)
    implementation(lifecycleViewmodelCompose)

    implementation(platform(composeBom))
    implementation(material3)
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

    implementation(mpAndroidChart)

    implementation(accompanistPermissions)
    implementation(accompanistSystemUi)

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

    implementation("androidx.bluetooth:bluetooth:1.0.0-alpha02")
    implementation("androidx.bluetooth:bluetooth-connect:1.0.0-alpha02")
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

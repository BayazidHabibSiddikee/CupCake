import java.security.MessageDigest
import java.net.URL
import java.io.FileOutputStream
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
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

        vectorDrawables.useSupportLibrary = true

        externalNativeBuild {
            cmake {
                arguments.add("-DCMAKE_BUILD_TYPE=Release")
                arguments.add("-DANDROID_ARM_NEON=TRUE")
                arguments.add("-DGGML_OPENMP=OFF")
                arguments.add("-DGGML_LLAMAFILE=OFF")
                cppFlags.add("-O3")
                cppFlags.add("-DNDEBUG")
                cFlags.add("-O3")
                cFlags.add("-DNDEBUG")
            }
        }
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
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.lifecycle.ExperimentalLifecycleApi"
        )
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = false
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packagingOptions {
        resources.excludes.add("META-INF/*")
        jniLibs.pickFirsts.add("libc++_shared.so")
        jniLibs.pickFirsts.add("libllama_jni.so")
    }

    androidResources {
        noCompress += "gguf"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// Download and verify Ollama binary at configure time
tasks.register("downloadOllamaBinary") {
    doLast {
        val binaryDir = project.file("src/main/assets/ollama")
        binaryDir.mkdirs()

        val binaryFile = project.file("$binaryDir/ollama")
        val expectedSha256 = project.property("ollamaBinarySha256") as String

        if (!binaryFile.exists() || !verifySha256(binaryFile, expectedSha256)) {
            val url = project.property("ollamaBinaryUrl") as String
            downloadAndVerify(url, binaryFile, expectedSha256)
            // Make executable
            binaryFile.setExecutable(true, false)
        }
    }
}

tasks.named("preBuild").configure { dependsOn("downloadOllamaBinary") }

fun verifySha256(file: File, expected: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = file.readBytes()
    val actual = digest.digest(bytes).joinToString("") { "%02x".format(it) }
    return actual == expected.lowercase()
}

fun downloadAndVerify(urlString: String, dest: File, expectedSha256: String) {
    val url = URL(urlString)
    val connection = url.openConnection()
    connection.connectTimeout = 30000
    connection.readTimeout = 120000
    
    val input = connection.getInputStream()
    val output = FileOutputStream(dest)
    val buffer = ByteArray(8192)
    var bytesRead: Int
    while (input.read(buffer).also { bytesRead = it } != -1) {
        output.write(buffer, 0, bytesRead)
    }
    output.close()
    input.close()
    
    // If it's a .tgz or .tar.zst, extract the ollama binary
    if (urlString.endsWith(".tgz") || urlString.endsWith(".tar.gz") || urlString.endsWith(".tar.zst")) {
        extractOllamaFromTgz(dest, dest.parentFile!!, dest)
    }
    // If it's a raw binary (no extension), just make it executable
    else {
        // Raw binary, nothing to extract
    }
    
    if (!verifySha256(dest, expectedSha256)) {
        throw GradleException("SHA-256 mismatch for downloaded Ollama binary")
    }
}

fun extractOllamaFromTgz(tgzFile: File, outputDir: File, dest: File) {
    // Determine extraction command based on file extension
    val cmd = if (tgzFile.name.endsWith(".tar.zst")) {
        // Use Python for .zst files (more portable)
        listOf("python3", "-c", "import tarfile, zstandard, sys; d = zstandard.ZstdDecompressor(); tar = tarfile.open(fileobj=d.stream_reader(open('${tgzFile.absolutePath}', 'rb'))); tar.extractall('${outputDir.absolutePath}')")
    } else {
        // Use tar for .tgz/.tar.gz files
        listOf("tar", "-xzf", tgzFile.absolutePath, "-C", outputDir.absolutePath)
    }
    val process = ProcessBuilder(*cmd.toTypedArray()).start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("Failed to extract Ollama binary from ${tgzFile.name}: exit code $exitCode")
    }
    // The binary should be at outputDir/ollama
    val extractedBinary = project.file("$outputDir/ollama")
    if (extractedBinary.exists()) {
        // Move to expected location if needed
        if (!dest.exists() || dest != extractedBinary) {
            extractedBinary.renameTo(dest)
        }
    }
    // Clean up archive
    tgzFile.delete()
}

dependencies {
    val coreSplashScreen = libs.androidx.core.splashscreen.get()
    val activityCompose = libs.androidx.activity.compose.get()
    val lifecycleViewmodelCompose = libs.androidx.lifecycle.viewmodel.compose.get()
    val lifecycleRuntimeCompose = libs.androidx.lifecycle.runtime.compose.get()
    val composeUi = libs.androidx.compose.ui.main.get()
    val composeFoundation = libs.androidx.compose.foundation.main.get()
    val composeAnimation = libs.androidx.compose.animation.get()
    val composeRuntime = libs.androidx.compose.runtime.get()
    val materialIconsCore = libs.androidx.compose.material.icons.core.get()
    val composeBom = libs.androidx.compose.bom.get()
    val material3 = libs.androidx.compose.material3.get()
    val materialIcons = libs.androidx.compose.material.icons.extended.get()
    val navigationCompose = libs.androidx.navigation.compose.get()
    val hilt = libs.hilt.android.get()
    val hiltCompiler = libs.hilt.compiler.get()
    val hiltNavigationCompose = libs.androidx.hilt.navigation.compose.get()
    val room = libs.androidx.room.runtime.get()
    val roomKtx = libs.androidx.room.ktx.get()
    val roomCompiler = libs.androidx.room.compiler.get()
    val datastore = libs.androidx.datastore.preferences.get()
    val coroutines = libs.kotlinx.coroutines.android.get()
    val flow = libs.kotlinx.coroutines.core.get()
    val serialization = libs.kotlinx.serialization.json.get()
    val serializationCore = libs.kotlinx.serialization.core.get()
    val coil = libs.coil.compose.get()
    val coilGif = libs.coil.gif.get()
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

    // OkHttp SSE for streaming (used by OllamaEngine)
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Ktor server for ESP WebSocket
    implementation("io.ktor:ktor-server-core:2.3.8")
    implementation("io.ktor:ktor-server-netty:2.3.8")
    implementation("io.ktor:ktor-server-websockets:2.3.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")

    implementation(coreSplashScreen)
    implementation(activityCompose)
    implementation(lifecycleViewmodelCompose)
    implementation(lifecycleRuntimeCompose)

    implementation(platform(composeBom))
    implementation(composeUi)
    implementation(composeFoundation)
    implementation(composeAnimation)
    implementation(composeRuntime)
    implementation(material3)
    implementation(materialIconsCore)
    implementation(materialIcons)
    implementation(navigationCompose)

    implementation(hilt)
    implementation(hiltNavigationCompose)
    ksp(hiltCompiler)

    implementation(room)
    implementation(roomKtx)
    ksp(roomCompiler)

    implementation(datastore)

    implementation(coroutines)
    implementation(flow)

    implementation(serialization)
    implementation(serializationCore)

    implementation(coil)
    implementation(coilGif)

    implementation(retrofit)
    implementation(retrofitScalars)
    implementation(okhttp)
    implementation(guava)

    implementation(mpAndroidChart)

    // TensorFlow Lite for sound event detection (YAMNet)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")


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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.lifecycle.ExperimentalLifecycleApi"
        )
    }
}

// NOTE: do not disable StripDebugSymbolsTask - packageDebug consumes its
// output, and disabling it silently drops all native libs from the APK.
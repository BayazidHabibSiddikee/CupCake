# ProGuard rules for CupCake

# Keep native JNI interfaces
-keep class com.cupcake.jni.** { *; }
-keepclassmembers class com.cupcake.jni.** {
    native <methods>;
}

# Keep llama.cpp JNI engine (loaded as libllama_jni, callbacks via reflection)
-keep class com.cupcake.ai.LlamaEngine { *; }
-keep class com.cupcake.ai.LlamaEngine$* { *; }
-keepclassmembers class com.cupcake.ai.LlamaEngine {
    native <methods>;
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keepclassmembers class dagger.hilt.** { *; }

# Keep Room entities and DAOs
-keep class com.cupcake.data.model.** { *; }
-keepclassmembers class com.cupcake.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.Dao { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable { *; }

# Keep serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Coil
-keep class coil.** { *; }

# Keep MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Keep Accompanist
-keep class com.google.accompanist.** { *; }

# Keep Bluetooth
-keep class androidx.bluetooth.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep standard library
-keep class java.** { *; }
-keep class kotlin.** { *; }
-keep class android.** { *; }

# Optimization
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively

# Don't warn about missing references
-dontwarn com.cupcake.jni.**
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**
-dontwarn coil.**
-dontwarn com.github.mikephil.charting.**
-dontwarn com.google.accompanist.**
-dontwarn androidx.bluetooth.**

# Ktor Netty engine: optional integrations not bundled on Android
-dontwarn reactor.**
-dontwarn io.netty.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
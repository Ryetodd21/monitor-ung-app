# Add project specific ProGuard rules here.

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Keep HiltWorker annotation processing output. The @HiltWorker annotation
# generates two things per worker class:
#   1. A binding in HiltWorkerFactory's `workers` map (so createWorker can
#      locate the assisted factory by class name).
#   2. A synthetic factory class (FooWorker_AssistedFactory) that Dagger
#      calls to obtain the Worker instance.
# R8 obfuscates these mappings and breaks the lookup at runtime — the
# symptom is "Could not instantiate <WorkerClass>: NoSuchMethodException
# <init>(Context, WorkerParameters)". This was the root cause of the
# 2026-09-29 crash loop on app launch.
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class **_AssistedFactory { *; }
-keep class **_AssistedFactory_Impl { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_GeneratedInjector { *; }

# Keep Kotlin metadata so Retrofit can recover suspend function generic types
# via reflection at runtime. Without this, R8 strips @kotlin.Metadata and
# Retrofit hits `java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`
# on the first `suspend fun foo(): Response<T>` invocation.
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation
-dontwarn kotlin.coroutines.Continuation

# Keep Retrofit
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Keep DTO generic types intact — Gson needs ParameterizedType for List<T>/Map<K,V>.
# Allow shrinking of unused fields but never obfuscate names so @SerializedName matches.
-keepclassmembers,allowobfuscation class com.monitorung.app.data.remote.dto.** {
    <fields>;
    <init>(...);
}
-keepnames @com.google.gson.annotations.SerializedName class *

# Keep Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes
-keep class com.monitorung.app.data.remote.dto.** { *; }
-keep class com.monitorung.app.domain.model.** { *; }

# Keep Room entities
-keep class com.monitorung.app.data.local.entity.** { *; }

# Keep MQTT
-keep class org.eclipse.paho.** { *; }

# SQLCipher — keep native lib and reflection-safe classes
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# EncryptedSharedPreferences / AndroidX Security
-keep class androidx.security.crypto.** { *; }

# Tink (hardware-backed key storage)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── Auto-generated warning suppression ──────────────────────
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ── Kotlin ───────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ── Kotlin Coroutines ────────────────────────────────────────
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── Retrofit2 ────────────────────────────────────────────────
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions.*

# ── OkHttp3 ──────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Jsoup ─────────────────────────────────────────────────────
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ── Readability4J ─────────────────────────────────────────────
-keep class net.dankito.readability4j.** { *; }
-dontwarn net.dankito.readability4j.**

# ── MediaPipe Tasks GenAI (Gemini Nano) ───────────────────────
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn com.google.mediapipe.**

# ── Room (entity/DAO safety) ──────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# ── kotlinx-collections-immutable ────────────────────────────
-dontwarn kotlinx.collections.immutable.**

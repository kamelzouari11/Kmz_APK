# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number info for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========== Moshi (reflection-based KotlinJsonAdapterFactory) ==========
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
# Keep all data model classes used with Moshi
-keep class com.example.simpleiptv.data.model.** { *; }
-keepclassmembers class com.example.simpleiptv.data.model.** { *; }

# Keep Kotlin metadata for Moshi reflection
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# ========== Retrofit ==========
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
# Keep Retrofit API interfaces
-keep interface com.example.simpleiptv.data.api.** { *; }

# ========== OkHttp ==========
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ========== Room ==========
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
# Keep Room entities and DAO result types
-keep class com.example.simpleiptv.data.local.entities.** { *; }
-keep class com.example.simpleiptv.data.local.ChannelWithProfile { *; }

# ========== Media3 / ExoPlayer ==========
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# ========== Compose — keep @Stable and @Immutable classes ==========
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }

# ========== Kotlin Coroutines ==========
-dontwarn kotlinx.coroutines.**

# ========== Misc ==========
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**
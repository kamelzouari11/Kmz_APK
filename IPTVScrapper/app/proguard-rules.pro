# Retrofit
-keepattributes Signature, InnerClasses, AnnotationDefault
-keepclassmembers class com.squareup.retrofit2.** { *; }
-dontwarn com.squareup.retrofit2.**
-keep class com.squareup.retrofit2.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Jsoup
-keep class org.jsoup.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

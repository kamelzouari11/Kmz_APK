# This is a configuration file for ProGuard.

# If you are using ProGuard, add the following lines:
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, SourceFile, LineNumberTable, *Annotation*, EnclosingMethod
-keep class **
-keepclassmembers class ** {
    *;
}

# Retrofit
-keep class retrofit.** { *; }
-keep class com.google.gson.** { *; }

# Kotlin
-keepclassmembers class kotlin.Metadata {
    *;
}

# Room
-keep class * extends androidx.room.RoomOpenHelper
-keep @interface androidx.room.Entity
-keep @interface androidx.room.Dao

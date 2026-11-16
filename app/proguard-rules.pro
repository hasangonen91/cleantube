# Keep NewPipeExtractor and its models (reflection/serialization)
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Security crypto
-keep class androidx.security.crypto.** { *; }

# Keep data models
-keep class com.cleantube.app.data.** { *; }

# General
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
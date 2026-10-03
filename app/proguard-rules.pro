# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep line numbers so crash reports can be deciphered
-keepattributes SourceFile,LineNumberTable

# Keep generic signatures and annotations for reflection and dependency injection (Hilt)
-keepattributes Signature,Exceptions,*Annotation*

# Keep standard Android components from being removed if referenced via XML or Intents
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ML Kit specific rules to prevent text recognition models and internal classes from being stripped in Release builds
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# tom_roush PDFBox and FontBox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.fontbox.**

# Room Database and SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# WorkManager
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.WorkerParameters { *; }

# Gson serialization and Domain Models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-keep class com.morphdrop.app.domain.model.** { *; }
-keep class com.morphdrop.app.data.local.entity.** { *; }

# BouncyCastle encryption/decryption
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**
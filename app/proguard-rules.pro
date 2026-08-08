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


# Apache POI relies on java.awt classes which are not fully present on Android.
# We tell R8 to ignore these missing class warnings.
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**
-dontwarn org.apache.poi.**


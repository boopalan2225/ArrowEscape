# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Crashlytics needs these to report a readable file and line number. Without
# them every obfuscated release stack trace is a dead end.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Crashlytics' own model classes must survive shrinking.
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Keep AdMob classes
-keep class com.google.android.gms.ads.** { *; }

# Keep data classes
-keep class com.clevercorner.arrowescape.game.model.** { *; }

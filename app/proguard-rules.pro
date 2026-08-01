# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Keep AdMob classes
-keep class com.google.android.gms.ads.** { *; }

# Keep data classes
-keep class com.example.arrowescape.game.model.** { *; }

-keep class org.webrtc.** { *; }
-keep class org.jni_zero.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { *; }
-keep class com.google.mlkit.common.sdkinternal.SharedPrefManager { *; }
-keepclassmembers class com.opencloudgaming.opennow.** {
    @kotlinx.serialization.Serializable *;
}

# --- Official release (R8) guards for OpenNOW-VR ---------------------------------
# The debug APK never ran R8. assembleRelease turns on minify + shrinkResources,
# so these classes now need explicit protection.
#
# Cardboard SDK registers its JNI methods statically (nativeInitialize,
# nativeHeadTrackerCreate, nativeDistortionRendererRenderEyeToDisplay, ...). The
# AAR's own consumer rules only keep @UsedByNative members; obfuscating a class or
# method name lets System.loadLibrary succeed but makes every subsequent JNI call
# fail on device. Keep the whole Java API surface.
-keep class com.google.cardboard.sdk.** { *; }
-keep class com.google.cardboard.proto.** { *; }

# VrSettingsStore writes enums to SharedPreferences with Enum.name and reads them
# back with Enum.valueOf(...). R8 is free to rename enum constants, which would
# crash on the first read of a preference saved by an older build and silently
# reset every VR setting after an upgrade. The VR package is tiny, so keeping it
# wholesale costs nothing and also protects the GL / Canvas / Camera2 glue.
-keep class com.opencloudgaming.opennow.vr.** { *; }
-keepclassmembers enum com.opencloudgaming.opennow.vr.** { *; }

-keepattributes SourceFile,LineNumberTable

-keep class com.frivasm.manosquehablan.api.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.camera.core.impl.** { *; }
-keep class androidx.camera.camera2.impl.** { *; }

-dontwarn libimage_processing_util_jni
-dontwarn **libimage_processing_util_jni**

-keep class androidx.core.content.FileProvider { *; }

# WorkManager keep rules to prevent R8 from breaking foreground service
-keep class androidx.work.impl.foreground.SystemForegroundService { *; }
-keep class androidx.work.impl.foreground.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
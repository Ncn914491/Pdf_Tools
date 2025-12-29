# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/lib/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# PDFBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Apache POI for Office documents
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.openxmlformats.schemas.**
-keep class com.microsoft.schemas.** { *; }
-dontwarn com.microsoft.schemas.**

# Apache Commons (used by POI)
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# XML parsing
-keep class javax.xml.** { *; }
-dontwarn javax.xml.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX DocumentFile
-keep class androidx.documentfile.** { *; }

# General
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn sun.misc.Unsafe
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**

# ETSI XML Signature (referenced by POI but not used on Android)
-dontwarn org.etsi.uri.x01903.v13.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.x2000.x09.xmldsig.**

# Jetpack Compose (Generally validated by R8 but good to be safe)
-keepattributes *Annotation*

# Keep OOXML schemas
-keepclassmembers class * extends org.apache.xmlbeans.XmlObject {
    public static ** Factory;
}

# Preserve MuPDF JNI classes and native method signatures
-keep class com.artifex.mupdf.fitz.** { *; }
-keepclassmembers class com.artifex.mupdf.fitz.** {
    native <methods>;
    <fields>;
    <init>(...);
}

# Preserve Room & Hilt generated classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.jetbrains.annotations.**

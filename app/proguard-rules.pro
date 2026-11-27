# Preserve MuPDF JNI classes and native method signatures
-keep class com.artifex.mupdf.fitz.** { *; }
-keepclassmembers class com.artifex.mupdf.fitz.** {
    native <methods>;
    <fields>;
    <init>(...);
}

# Preserve OpenCV JNI classes
-keep class org.opencv.** { *; }

# Preserve Tesseract4Android & Leptonica JNI classes
-keep class com.googlecode.tesseract.** { *; }
-keep class com.googlecode.leptonica.** { *; }

# Preserve Room & Hilt generated classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.jetbrains.annotations.**

# ONNX Runtime Android (DocAligner Neural Corner Detector)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

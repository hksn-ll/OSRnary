# ProGuard / R8 rules for the GabAI release build.
# R8 shrinking + obfuscation is enabled in app/build.gradle.kts (isMinifyEnabled = true).

# Keep line numbers for readable crash stack traces, but hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlin.**

# --- Firebase / Firestore ---
# Firestore uses reflection to (de)serialize model classes. Keep any model
# classes and their members so field mapping keeps working after obfuscation.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class com.google.firebase.** { *; }
-keep class com.example.gabai.**$* { *; }
-keepclassmembers class com.example.gabai.** {
    public <init>(...);
    public *;
}
-dontwarn com.google.firebase.**

# --- Google Generative AI (Gemini) SDK ---
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# --- ML Kit (text recognition / language id) ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- PDFBox (tom-roush) & PDF viewer ---
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class com.github.barteksc.pdfviewer.** { *; }
-keep class com.shockwave.**

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Guava (pulled in by CameraX) ---
-dontwarn com.google.common.**
-dontwarn java.lang.SafeVarargs

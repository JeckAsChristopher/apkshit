# MIDE ProGuard Rules

# Keep ECJ compiler classes
-keep class org.eclipse.jdt.** { *; }
-keep class org.eclipse.core.** { *; }
-dontwarn org.eclipse.**

# Keep Sora Editor
-keep class io.github.rosemoe.sora.** { *; }
-dontwarn io.github.rosemoe.**

# Keep Termux terminal classes
-keep class com.termux.** { *; }
-dontwarn com.termux.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Apache Commons Compress
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep MIDE data classes
-keep class com.mide.ide.plugin.PluginManifest { *; }
-keep class com.mide.ide.project.** { *; }
-keep class com.mide.ide.compiler.BuildError { *; }
-keep class com.mide.ide.downloader.ToolDownloadManager$DownloadStatus { *; }
-keep class com.mide.ide.downloader.ToolDownloadManager$StorePlugin { *; }

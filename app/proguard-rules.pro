# Keep our JNI interface — method names must match C++ mangled names exactly
-keep class com.locai.app.LlamaJNI { *; }
-keep interface com.locai.app.LlamaJNI$TokenCallback { *; }
-keep class com.locai.app.** { *; }

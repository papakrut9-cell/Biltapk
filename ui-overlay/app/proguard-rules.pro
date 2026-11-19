# Правила поверх дефолтных: сохраняем весь JNI/FFI-слой движка.

-keep class com.arm.aichat.* { *; }
-keep class com.arm.aichat.gguf.* { *; }
-keep class com.arm.aichat.internal.* { *; }

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

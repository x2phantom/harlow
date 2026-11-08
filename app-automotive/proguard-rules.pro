# ProGuard rules for app-automotive
# Automotive-specific rules:

# Navigation components
-keep class dev.harlow.automotive.** { *; }

# Keep sealed interface variants for HarlowResult
-keep class dev.harlow.core.common.HarlowResult { *; }
-keep class dev.harlow.core.common.HarlowResult$Success { *; }
-keep class dev.harlow.core.common.HarlowResult$Error { *; }

# Keep Room entities
-keep class dev.harlow.core.database.entity.** { *; }
-keep class dev.harlow.core.database.dao.** { *; }

# Keep domain models
-keep class dev.harlow.core.model.** { *; }

# Release builds keep only warnings and errors: debug/info logs can carry server URLs, item ids and user names
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

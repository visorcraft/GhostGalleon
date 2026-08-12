# Ghost Galleon — release R8 rules.
# Keep public entry points and anything the system binds by name.

# Activities / Application (manifest)
-keep public class com.visorcraft.ghostgalleon.GhostGalleonApp
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# BuildConfig fields read from code
-keepclassmembers class com.visorcraft.ghostgalleon.BuildConfig {
    public static <fields>;
}

# Enum valueOf used in SettingsStore keyMap / Action
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# View tags / ids are resource ints — no keep needed.
# org.json is platform; no reflection on our Settings data class fields
# (SettingsStore uses explicit JSONObject put/get).

# Strip log noise in release (optional speed micro-gain)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# The Xposed module's entry point is named only from assets/xposed_init, and the framework loads it
# by that string -- nothing in the app's own code references it. R8 sees an unreachable class and
# strips it, which produces a release build whose module silently never loads, so keep the whole
# package by name.
-keep class com.elitedarkkaiser.redmagic.xposed.** { *; }

# The Xposed API is provided by the framework at runtime and is compileOnly here, so R8 sees the
# references as unresolved. They resolve fine once LSPosed loads the module.
-dontwarn de.robv.android.xposed.**

# OverlayCli is launched by name from a root shell -- "app_process ... OverlayCli apply <spec>" --
# so nothing in the app's own code references it and R8 would strip it exactly as it strips the
# Xposed entry point above, leaving a release build whose theming silently does nothing. Keep the
# class and its main(), and keep the enclosing package's names stable while we are here: the shell
# command embeds the fully-qualified name as a literal string.
-keep class com.elitedarkkaiser.redmagic.systemtheme.OverlayCli { *; }
-keepclassmembers class com.elitedarkkaiser.redmagic.systemtheme.OverlayCli {
    public static void main(java.lang.String[]);
}

# Launched by name to discover the installed vendor input ABI as root.
-keep class com.elitedarkkaiser.redmagic.gametrigger.NativeTgkAbiCli { *; }




# Upgrade cleanup can only clear a legacy native refresh request; it cannot set a rate.
-keep class com.elitedarkkaiser.redmagic.LegacyHardwareCleanupCli { *; }

package de.robv.android.xposed.callbacks;

/** Stub: see xposed-api/README.md. */
public abstract class XC_LoadPackage {
    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
    }
}

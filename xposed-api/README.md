# xposed-api (compile-time stubs)

Minimal stubs for the small slice of the Xposed API that the bundled window-fix module uses
(`app/src/main/java/com/elitedarkkaiser/redmagic/xposed`).

They exist because the real artifact, `de.robv.android.xposed:api:82`, lives only on
`api.xposed.info`, which this project's build environment cannot reach; the JitPack mirror of
`rovo89/XposedBridge` ships only Android hidden-API stubs, not these classes.

**These are signatures, not implementations.** They are consumed with `compileOnly`, so they are
never packaged into the APK — at runtime the Xposed/LSPosed framework provides the real classes,
and the app's dex links against those.

That makes the method signatures load-bearing: a JVM call site encodes the full descriptor,
return type included, so a stub that differs from the real API compiles cleanly and then fails at
runtime with `NoSuchMethodError`. Anything changed here must match the upstream API exactly. If
`de.robv.android.xposed:api` ever becomes reachable, replace this module with it.

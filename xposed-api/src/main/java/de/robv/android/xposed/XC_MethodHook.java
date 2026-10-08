package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Stub: see xposed-api/README.md. */
public class XC_MethodHook {
    public XC_MethodHook() {
    }

    public XC_MethodHook(int priority) {
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        public Object getResult() {
            return null;
        }

        public void setResult(Object result) {
        }

        public Throwable getThrowable() {
            return null;
        }

        public void setThrowable(Throwable throwable) {
        }
    }

    /** Return type of {@link XposedBridge#hookMethod}; part of that call's descriptor. */
    public class Unhook {
        public void unhook() {
        }
    }
}

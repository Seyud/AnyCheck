package com.hunter.api.rposed.callbacks;

import android.content.pm.ApplicationInfo;

import com.hunter.api.rposed.IRposedHookLoadPackage;
import com.hunter.api.rposed.RposedBridge;

/**
 * This class is only used for internal purposes, except for the {@link LoadPackageParam}
 * subclass.
 */
@SuppressWarnings("unused")
public abstract class RC_LoadPackage extends XCallback implements IRposedHookLoadPackage {
    /**
     * Creates a new callback with default priority.
     * @hide
     */
    @SuppressWarnings("deprecation")
    public RC_LoadPackage() {
        super();
    }

    /**
     * Creates a new callback with a specific priority.
     *
     * @param priority See {@link XCallback#priority}.
     * @hide
     */
    public RC_LoadPackage(int priority) {
        super(priority);
    }

    /**
     * Wraps information about the app being loaded.
     */
    public static final class LoadPackageParam extends XCallback.Param {
        /** @hide */
        public LoadPackageParam(RposedBridge.CopyOnWriteSortedSet<RC_LoadPackage> callbacks) {
            super(callbacks);
        }

        /** The name of the package being loaded. */
        public String packageName;

        /** The process in which the package is executed. */
        public String processName;

        /** The ClassLoader used for this package. */
        public ClassLoader classLoader;

        /** More information about the application being loaded. */
        public ApplicationInfo appInfo;

        /** Set to {@code true} if this is the first (and main) application for this process. */
        public boolean isFirstApplication;
    }

    /** @hide */
    @Override
    protected void call(Param param) throws Throwable {
        if (param instanceof LoadPackageParam) {
            handleLoadPackage((LoadPackageParam) param);
        }
    }
}
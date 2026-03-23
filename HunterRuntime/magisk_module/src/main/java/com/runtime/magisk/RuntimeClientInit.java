package com.runtime.magisk;

import static android.provider.Settings.Secure.ANDROID_ID;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;

import com.hunter.api.rposed.RC_MethodHook;
import com.hunter.api.rposed.RposedBridge;
import com.runtime.magisk.utils.CLog;
import com.runtime.magisk.utils.HiddenAPIEnforcementPolicyUtils;

import dalvik.system.InMemoryDexClassLoader;


/**
 * @author Zhenxi on 2023/11/30
 */
public class RuntimeClientInit {
    public static final int PER_USER_RANGE = 100000;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenAPIEnforcementPolicyUtils.passApiCheck();
        }
    }

    static boolean clientIsInit = false;


    /**
     * 在Context创建完毕进行分发事件 。
     */
    public static void RuntimeClientInitStart(Context context, IRuntimeService rms) {
        if (context == null) {
            CLog.e("RuntimeClientInitStart context == null !!! ");
            return;
        }
        if (clientIsInit) return;
        InMemoryDexClassLoader classLoader;
        try {
            CLog.e("RuntimeClientInitStart ! " + context.getPackageName());
            //test tag time
            RposedBridge.hookAllMethods(Application.class,
                    "onCreate", new RC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            CLog.e("onCreate get callback after ");
                        }

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            CLog.e("onCreate get callback before ");
                        }
                    });
            clientIsInit = true;
        } catch (Throwable e) {
            CLog.e("RuntimeClientInitStart error " + e);
        }
    }
}

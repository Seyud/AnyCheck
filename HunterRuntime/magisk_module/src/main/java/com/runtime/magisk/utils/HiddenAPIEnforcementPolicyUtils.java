package com.runtime.magisk.utils;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;


import com.hunter.buildsrc.RunTimeConstants;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Method;

@Keep
public class HiddenAPIEnforcementPolicyUtils {

    private static Method addWhiteListMethod;

    private static Object vmRuntime;

    private static boolean hasInit = false;


    private static void init() {
        try {
            Method getMethodMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Method forNameMethod = Class.class.getDeclaredMethod("forName", String.class);

            Class<?> vmRuntimeClass = (Class<?>) forNameMethod.invoke(null, "dalvik.system.VMRuntime");
            addWhiteListMethod = (Method) getMethodMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions",
                    new Class[]{String[].class});
            Method getVMRuntimeMethod = (Method) getMethodMethod.invoke(vmRuntimeClass, "getRuntime", null);
            if (getVMRuntimeMethod != null) {
                vmRuntime = getVMRuntimeMethod.invoke(null);
            }
            hasInit = true;
        } catch (Exception e) {
            Log.e(RunTimeConstants.TAG, "error get methods", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Keep
    public static void passApiCheck() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        try {
            if (BuildCompat.isR()) {
                HiddenApiBypass.setHiddenApiExemptions(
                        "Landroid/content/pm/ApplicationInfo;",
                        "Landroid/",
                        "Lcom/android/",
                        "Ljava/lang/",
                        "Ljava/net/",
                        "Ldalvik/system/",
                        "Llibcore/io/",
                        "Lsun/misc/",
                        "Lhuawei/",
                        "Ldalvik/system/",
                        "Lcom/zhenxi/",
                        "Ltest/"
                );
                return;
            }
            if (!hasInit) {
                init();
            }
            if (BuildCompat.isQ() ) {
                addReflectionWhiteList(
                        "Landroid/",
                        "Lcom/android/",
                        "Ljava/lang/",
                        "Ldalvik/system/",
                        "Llibcore/io/",
                        "Lsun/misc/",
                        "Ldalvik/system/"
                );
            } else {
                addReflectionWhiteList(
                        "Landroid/",
                        "Lcom/android/",
                        "Ljava/lang/",
                        "Ldalvik/system/",
                        "Llibcore/io/",
                        "Lsun/misc/"
                );
            }
            //Log.e(RunTimeConstants.TAG, "init pass hide api check sucess! ");
        } catch (Throwable throwable) {
            Log.e(RunTimeConstants.TAG, "pass Hidden API enforcement policy failed", throwable);
        }
    }



    //methidSigs like Lcom/swift/sandhook/utils/ReflectionUtils;->vmRuntime:java/lang/Object; (from hidden policy list)
    private static void addReflectionWhiteList(String... memberSigs) throws Throwable {
        addWhiteListMethod.invoke(vmRuntime, new Object[]{memberSigs});
    }
}

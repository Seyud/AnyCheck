package com.runtime.magisk.server;

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.LruCache;

import com.hunter.api.rposed.RposedHelpers;
import com.runtime.magisk.BuildConfig;
import com.runtime.magisk.IRuntimeService;

import com.runtime.magisk.RequestInitBean;
import com.runtime.magisk.RuntimeServiceInit;
import com.runtime.magisk.MagiskEngine;
import com.runtime.magisk.utils.CLog;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Zhenxi on 2023/11/26
 * 这个服务Runtime驻留在服务端的服务。
 */
public class RuntimeManagerService extends IRuntimeService.Stub {
    public static final String RUNTIME_JAR_PATH = "system/framework/" + BuildConfig.RuntimeDexName + ".jar";
    public static final String RUNTIME_TEMP_DEX_PATH = "data/system/" + BuildConfig.RuntimeDexName + "_temp_dex";
    public static final String RUNTIME_TRACER_FILE_PATH = "data/system/systemTracer";

    /**
     * system service classloader
     */
    private static final ArrayList<ClassLoader> system_classLoader_list = new ArrayList<>();

    private RuntimeManagerService() {

    }

    private static IPackageManager mPm;
    /**
     * app name & uid
     */
    private static final LruCache<Integer, String[]> mAppUidMap = new LruCache<>(128) {
        @Override
        protected String[] create(Integer key) {
            try {
                return mPm.getPackagesForUid(key);
            } catch (RemoteException e) {
                // should never happen, we run in the same process as PMS
                CLog.e("getPackagesForUid failed", e);
                return null;
            }
        }
    };
    /**
     * system_service context
     */
    private static Context mContext;

    private static final class InstanceHolder {
        private static final RuntimeManagerService instance = new RuntimeManagerService();
    }

    public static RuntimeManagerService getInstance() {
        return InstanceHolder.instance;
    }

    /**
     * 获取PackageManager实例
     */
    public void setPackageManager(IBinder service) {
        mPm = IPackageManager.Stub.asInterface(service);
    }

    public static IPackageManager getPackageManager() {
        if (mPm == null) {
            CLog.e("RuntimeManagerService getPackageManager mPm == null");
            return null;
        }
        return mPm;
    }

    public static PackageInfo getCallPackageInfo(String package_name, int user_id) {
        if (mPm == null) {
            CLog.e("RuntimeManagerService getCallPackageInfo mPm == null");
            return null;
        }
        try {
            return mPm.getPackageInfo(package_name, 0, user_id);
        } catch (Throwable e) {
            CLog.e("RuntimeManagerService getCallPackageInfo error " + e, e);
        }
        return null;
    }

    public static void initContext() {
        if (mContext == null) {
            ActivityThread activityThread = ActivityThread.currentActivityThread();
            Context context = mirror.android.app.ActivityThread.
                    REF.method("getSystemContext").call(activityThread);
            if (context == null) {
                CLog.e("No context for register package monitor");
                return;
            }
            mContext = context;
        }
    }


    public static void addPackageLoaderInfo(ClassLoader loader) {
        if (loader != null) {
            system_classLoader_list.add(loader);
        }
    }

    /**
     * find class
     */
    public static Class<?> findclass(String class_name) {
        Class<?> clazz = null;
        if (system_classLoader_list != null) {
            for (ClassLoader loader : system_classLoader_list) {
                try {
                    clazz = RposedHelpers.findClass(class_name, loader);
                    if (clazz != null) {
                        return clazz;
                    }
                } catch (Throwable ignored) {

                }
            }
        }

        try {
            clazz = RposedHelpers.findClass(class_name,
                    Thread.currentThread().getContextClassLoader());
        } catch (Throwable ignored) {

        }
        if (clazz == null) {
            try {
                clazz = RposedHelpers.findClass(class_name,
                        getContext().getClassLoader());
            } catch (Throwable ignored) {

            }
        }
        if (clazz == null) {
            CLog.e("RuntimeManagerService findclass == null " + class_name);
        }
        return clazz;
    }

    /**
     * @return return system service context
     */
    public static Context getContext() {
        if (mContext == null) {
            initContext();
        }
        return mContext;
    }

    /**
     * 添加服务规则,服务端二次确认,判断是否来自我们需要的修改的apk请求。
     * hook多个Apk,暂时忽略binder和dex请求判断。
     */
    @SuppressWarnings("unused")
    public boolean isEnabledFor() {
        String callAppName = getCallAppName();
        if (callAppName == null) {
            //CLog.e("RuntimeManagerService isEnabledFor == null");
            return false;
        }
        if (callAppName.equals(BuildConfig.tagPackageName)) {
            return true;
        }
        CLog.i("RuntimeManagerService stop the request " + callAppName);
        return false;
    }

    @Override
    public String TestRuntime() {
        return "test ipc is ok";
    }

    @Override
    public String getAppName() {
        return getCallAppName();
    }

    /**
     * 判断当前调用者是否是init请求的包
     */
    @SuppressWarnings("unused")
    public static boolean isMatchCallPackageInfo(RequestInitBean bean) {
        if (bean == null) {
            CLog.e("RuntimeManagerService isMatchCallPackageInfo error bean == null");
            return false;
        }
        return bean.initFpPackageName.equals(BuildConfig.tagPackageName);
    }


    public static String getCallAppName() {
        String[] packages = mAppUidMap.get(Binder.getCallingUid());
        if (packages != null && packages.length == 1) {
            return packages[0];
        }
        return null;
    }

    private static ArrayList<SharedMemory> preloadDex = null;

    private static SharedMemory readDex(InputStream in) throws IOException, ErrnoException {
        var memory = SharedMemory.create(null, in.available());
        var byteBuffer = memory.mapReadWrite();
        Channels.newChannel(in).read(byteBuffer);
        SharedMemory.unmap(byteBuffer);
        memory.setProtect(OsConstants.PROT_READ);
        return memory;
    }

    public synchronized static ArrayList<SharedMemory> getPreloadDex(List<File> dexFiles) {
        if (preloadDex == null) {
            preloadDex = new ArrayList<>();
            for (File dex_file : dexFiles) {
                try (var is = new FileInputStream(dex_file)) {
                    preloadDex.add(readDex(is));
                } catch (Throwable e) {
                    CLog.e("RuntimeManagerService preload dex", e);
                    return null;
                }
            }
        }
        return preloadDex;
    }


}

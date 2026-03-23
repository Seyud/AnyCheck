package com.hunter.runtime;

import android.content.Context;
import android.os.Build;
import android.os.Process;


import com.hunter.buildsrc.RunTimeConstants;
import com.hunter.external.utils.CLog;
import com.hunter.runtime.utils.BuildCompat;

import java.io.File;
import java.util.ArrayList;

/**
 * @author Zhenxi on 2023/2/10
 */
public class NativiEngine {

    /**
     * 分析对应的so的调用细节
     *
     * @param list 保存了需要监听的so集合
     * @param path 是否保存到文件里面,不保存则传null
     */
    public static native void Analysis(ArrayList<?> list,String path);

    /**
     * 获取需要隐藏的列表集合
     */
    private static native ArrayList<String> getNativeHideItemList();

    public static native void Test(Context context);

    /**
     * 隐藏Maps痕迹,不能自己隐藏自己
     */
    private static native void hideMapsMarks(ArrayList<String> markList, String packageName);

    private static native void hideLinkerMarks(ArrayList<String> markList);

    public static void hideLinkerAndMaps(Context context) {
        ArrayList<String> nativeHideItemList = getNativeHideItemList();
        hideLinkerMarks(nativeHideItemList);
        hideMapsMarks(nativeHideItemList, context.getPackageName());
    }

    //---------------------------------------------------------------
    /**
     * 获取重定位(以后)的路径,比如/SDcard重定位以后的路径
     *
     * sdcard/->/sdcard/mysdcard/
     */
    public static String getRedirectedPath(String origPath) {
        try {
            return nativeGetRedirectedPath(origPath);
        } catch (Throwable e) {
            CLog.e("getRedirectedPath error", e);
        }

        return origPath;
    }

    /**
     * 获取重定向(之前)的路径
     * 传入一个已经被重定位的路径,得到他原始的路径。
     */
    public static String reverseRedirectedPath(String origPath) {
        try {
            return nativeReverseRedirectedPath(origPath);
        } catch (Throwable e) {
            CLog.e("reverseRedirectedPath error", e);
        }
        return origPath;
    }

    /**
     * 重定位目录
     */
    public static void redirectDirectory(String origPath, String newPath) {
        if (origPath == null || newPath == null) {
            CLog.e( "redirectDirectory Error ");
            return;
        }
        //Log.e(TAG, "redirectDirectory "+origPath +" -> "+newPath);
        if (!origPath.endsWith("/")) {
            origPath = origPath + "/";
        }
        if (!newPath.endsWith("/")) {
            newPath = newPath + "/";
        }
        try {
            //Log.i(TAG, String.format("redirect origin  directory %s -> %s", origPath, newPath));
            nativeIORedirect(origPath, newPath);
        } catch (Throwable e) {
            CLog.e("redirectDirectory error", e);
        }
    }

    /**
     * 重定位文件
     */
    public static void redirectFile(String origPath, String newPath) {
        if (origPath == null || newPath == null) {
            CLog.e(  "redirectFile Error ");
            return;
        }
        if (origPath.endsWith("/")) {
            origPath = origPath.substring(0, origPath.length() - 1);
        }
        if (newPath.endsWith("/")) {
            newPath = newPath.substring(0, newPath.length() - 1);
        }

        try {
            //Log.i(TAG, String.format("redirect origin  file %s -> %s", origPath, newPath));
            nativeIORedirect(origPath, newPath);
        } catch (Throwable e) {
            CLog.e("redirectFile error", e);
        }
    }


    /**
     * 添加IO白名单
     *
     * @param path 如果是目录，需要以/结尾
     */
    public static void whitelist(String path) {
        try {
            if (new File(path).isDirectory() && !path.endsWith("/")) {
                path = path + "/";
            }
            //Log.i(TAG, "white list file:" + path);
            nativeIOWhitelist(path);
        } catch (Throwable e) {
            CLog.e("whitelist error", e);
        }
    }

    /**
     * 禁止该路径被读取
     */
    public static void forbid(String path) {
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        try {
            nativeIOForbid(path);
            CLog.i("forbid file:" + path);
        } catch (Throwable e) {
            CLog.e("forbid error", e);
        }
    }


    public static void enableIORedirect() {
        try {

            File file = RuntimeEnvironment.envRuntimeNativeCacheDir();

            //参考VA写法
            String soPath32 = new File(HunterRuntime.originApplicationInfo.nativeLibraryDir,
                    "lib" + HunterRuntime.RUNTIME_NATIVE_LIB_NAME + ".so").getAbsolutePath();
            String soPath64 = new File(HunterRuntime.originApplicationInfo.nativeLibraryDir,
                    "lib" + HunterRuntime.RUNTIME_NATIVE_LIB64_NAME + ".so").getAbsolutePath();

            if (Process.is64Bit()) {
                //如果64位把32位的arm64替换成arm
                soPath32 = soPath32.replaceAll("arm64", "arm");
            } else {
                soPath64 = soPath64.replaceAll("arm", "arm64");
            }

            CLog.i("32bit  So Path  " + soPath32);
            CLog.i("64bit  So Path  " + soPath64);
            CLog.i("runtime native cache path    " + file.getPath());

            //暂未支持64暂且传空
            nativeEnableIORedirect(
                    soPath32,
                    soPath64,
                    file.getPath(),
                    Build.VERSION.SDK_INT,
                    BuildCompat.getPreviewSDKInt()
            );

        } catch (Throwable e) {
            CLog.e("enableIORedirect error", e);
        }
    }


    private static native String nativeReverseRedirectedPath(String redirectedPath);

    private static native String nativeGetRedirectedPath(String orgPath);

    private static native void nativeIORedirect(String origPath, String newPath);

    private static native void nativeIOWhitelist(String path);

    private static native void nativeIOForbid(String path);

    private static native void nativeEnableIORedirect(String soPath, String soPath64, String cachePath, int apiLevel, int previewApiLevel);



}

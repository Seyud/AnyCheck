package com.hunter.runtime;

import static com.hunter.external.utils.FileUtils.makeSureDirExist;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import android.system.Os;

import androidx.annotation.Keep;

import com.hunter.BuildConfig;
import com.hunter.api.rposed.RposedHelpers;
import com.hunter.buildsrc.RunTimeConstants;
import com.hunter.external.extend.ChooseUtils;
import com.hunter.external.utils.AppUtils;
import com.hunter.external.utils.CLog;
import com.hunter.external.utils.FileUtils;
import com.hunter.external.utils.GsonUtils;
import com.hunter.external.utils.ThreadUtils;


import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;

public class HunterRuntime {
    /**
     * ActivityThread instance
     */
    @Keep
    public static Object mainThread;
    @Keep
    public static String processName;
    @Keep
    public static String packageName;
    @Keep
    public static Object theLoadApk = null;
    @Keep
    public static Application realApplication = null;
    @Keep
    public static ApplicationInfo originApplicationInfo;
    /**
     * 这个字段表示原来App的ApplicationName
     * 有3种情况
     * 1,原始App不存在Application 此时等于 null
     * 2,原始App存在Application 等于原始Application的classname
     * 3,如果加上 -dex 的话此字段等于原始Application的classname
     */
    @Keep
    public static String originApplicationName;

    @Keep
    public static Object mBoundApplication;

    /**
     * 这个字段标识是否是Application进行替换
     * true的是通过Application name的方式进行初始化。
     * false是通过静态代码块进行初始化
     */
    @Keep
    public static boolean isReplaceApplication = false;

    public static final String RUNTIME_NATIVE_LIB_NAME = BuildConfig.runtime_lib_name;
    //兼容64位
    public static final String RUNTIME_NATIVE_LIB64_NAME = BuildConfig.runtime_lib_name + "64";

    static {
        try {
            CLog.e("hunter runtime static init ");
            //TestClass.test(null);
            //绕过反射限制,第一优先级
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("");
            }
            //加载Sandhook Hook So
            String soName;
            if (Process.is64Bit()) {
                soName = RUNTIME_NATIVE_LIB64_NAME;
            } else {
                soName = RUNTIME_NATIVE_LIB_NAME;
            }
            CLog.e(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>> load so for name :  " + soName + " " + AppUtils.getProcessName() + " " + Process.myPid());

            System.loadLibrary(soName);


        } catch (Throwable e) {
            CLog.e("static loadLibrary error", e);
        }
    }

    /**
     * dex插入核心入口需要keep
     */
    @Keep
    @SuppressLint("PrivateApi")
    public static void startFromStaticMethod() {
        try {

            CLog.e(">>>>>>>>>>>>>> start from static method ,process name -> " + AppUtils.getProcessName()+" "+ Os.getpid());

            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);
            Field declaredField = activityThreadClass.getDeclaredField("mBoundApplication");
            declaredField.setAccessible(true);
            Object mBoundApplication = declaredField.get(currentActivityThread);
            Field applicationInfoField = Objects.requireNonNull(mBoundApplication).getClass().getDeclaredField("info");
            applicationInfoField.setAccessible(true);
            Object applicationInfo = applicationInfoField.get(mBoundApplication);

            Class<?> ContextImplClazz = Class.forName("android.app.ContextImpl");
            Method createAppContext = ContextImplClazz.getDeclaredMethod("createAppContext",
                    activityThreadClass, Objects.requireNonNull(applicationInfo).getClass());
            createAppContext.setAccessible(true);
            Object applicationContext = null;
            try {
                applicationContext = createAppContext.invoke(null, currentActivityThread, applicationInfo);
            } catch (Throwable ignored) {

            }
            if (applicationContext instanceof Context) {
                //如果这行代码没有执行，感染的时候需要加-factory
                CLog.i(">>>>>>>>>>>   get applicationContext  sucess !");
                startRuntime((Context) applicationContext);
            } else {
                CLog.e(">>>>>>>>>>>>   static get context fail ");
                System.exit(0);
            }
        } catch (Throwable throwable) {
            CLog.e(">>>>>>>>>>>>  start from static method error:", throwable);
            System.exit(0);
        }
    }




    public static void startRuntime(Context context) throws Exception {
        try {
            if (context == null) {
                CLog.e("startRuntime init error context == null ,system.exit");
                System.exit(0);
                return;
            }
            initToolkit(context);


            CLog.e("hunter runtime init success 111 !");
        } catch (Throwable e) {
            CLog.e("runtime init fail " + e);
        }
    }

    private static void IOTest(Context context) {
        try {
            File dataDir = context.getDataDir();
            CLog.e("dataDir info -> "+dataDir.getPath());

            File mydata = makeSureDirExist(new File(context.getDataDir()+"/mydata"));

            NativiEngine.redirectDirectory(dataDir.getPath(),mydata.getPath());
            NativiEngine.redirectDirectory(
                    "/data/data/"+context.getPackageName(),mydata.getPath());

            NativiEngine.enableIORedirect();

            com.hunter.external.apache.commons.
                    io.FileUtils.writeStringToFile(
                            new File(dataDir,"test.txt"),"123456","UTF-8");

            CLog.e("file save success !!");
        } catch (Throwable e) {
            CLog.e("HunterRuntime->IOTest error "+context,e);
        }
    }


    private static void initToolkit(Context context) {
        originApplicationInfo = context.getApplicationInfo();

        HunterToolkit.packageName = context.getPackageName();
        HunterToolkit.processName = AppUtils.getProcessName();

        mainThread = RposedHelpers.callStaticMethod(RposedHelpers.findClass("android.app.ActivityThread", ClassLoader.getSystemClassLoader()), "currentActivityThread");
        mBoundApplication = RposedHelpers.getObjectField(mainThread, "mBoundApplication");
        theLoadApk = RposedHelpers.getObjectField(mBoundApplication, "info");
    }

    /**
     * 获取内存全部对象的内存信息,并且保存
     * 主要用于分析内存结构，确定什么对象保存了设备指纹等关键字段
     * <p>
     * 大约需要运行十分钟所以即可全部遍历
     */
    @SuppressWarnings("unused")
    private static void getAllObjectInfo(Context context, File file) {
        //优先保存主进程
        ThreadUtils.runOnMainThread(() -> {
            CLog.e("start get all object !!!");
            //ArrayList<Object> choose = ChooseUtils.choose(String.class, true);
            ArrayList<Object> choose = ChooseUtils.choose(Object.class, true);
            int size = choose.size();
            CLog.e("all count size ->  " + size);
            for (int i = 0; i < size; i++) {
                Object o = choose.get(i);
                String s = GsonUtils.obj2str(o);
                if (s != null) {
                    String c = o.getClass().getName();
                    String s1 = c + " " + s + "\n";
                    CLog.e(HunterToolkit.processName + " " + i + " " + size + "  " + s1);
                    FileUtils.saveStringNoClose(HunterToolkit.getContext(), s1, file);
                }
            }
        }, 10 * 1000);
    }
}

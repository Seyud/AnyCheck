package com.runtime.magisk;

import android.app.Application;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Keep;

import com.hunter.api.rposed.RC_MethodHook;
import com.hunter.api.rposed.RposedBridge;
import com.hunter.api.rposed.RposedHelpers;
import com.runtime.magisk.server.BinderServiceProxy;
import com.runtime.magisk.server.RuntimeManagerService;
import com.runtime.magisk.utils.CLog;
import com.runtime.magisk.utils.reflect.Reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;


import dalvik.system.InMemoryDexClassLoader;
import mirror.android.app.ActivityThread;
import mirror.android.os.ServiceManager;

/**
 * @author Zhenxi on 2023/11/22
 */
public class MainStub {
    public static final String ANDROID = "android";
    public static final String TARGET_BINDER_SERVICE_NAME = Context.CLIPBOARD_SERVICE;
    public static final String TARGET_BINDER_SERVICE_DESCRIPTOR = "android.content.IClipboard";
    private static boolean classLoaderReady;
    /**
     * runtime need service
     */
    private static boolean
            clipboardServiceReplaced,
            packageManagerReady,
            activityManagerReady;


    public static boolean mainZygote;
    private static boolean inited;

    private static void initUncaughtExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            CLog.e("error for thread: " + t.getName() + "  " + e.getMessage());
            CLog.e("UncaughtExceptionHandler error \n"+Log.getStackTraceString(e));
            //release包增加异常捕获
            if (defaultUncaughtExceptionHandler != null && !BuildConfig.DEBUG) {
                defaultUncaughtExceptionHandler.uncaughtException(t, e);
                CLog.e("uncaughtException success ");
            }
        });
    }

    /**
     * app process & SystemServer commonInit
     */
    private static void commonInit() {
        if (inited) return;
        CLog.i("MainStub commonInit");
        MagiskEngine.systemServerNativeInit();
        initUncaughtExceptionHandler();
        inited = true;
    }


    /**
     *
     */
    @SuppressWarnings("unused")
    @Keep
    public static void onSystemServerStart() {
        CLog.i(">>>>>>>>>>>> MainStub onSystemServerStart System server is started!");
        commonInit();
        mainZygote = true;
        RuntimeManagerService rms = RuntimeManagerService.getInstance();

        Object origServiceManager = ServiceManager.getIServiceManager.callStatic();
        //在SystemServer还没有添加系统服务的时候进行动态代理
        ServiceManager.sServiceManager.setStaticValue(Reflection.on("android.os.IServiceManager")
                .proxy((proxy, method, args) -> {
                    if ("addService".equals(method.getName())) {
                        String serviceName = (String) args[0];
                        IBinder binder = (IBinder) args[1];
                        //CLog.w("[" + serviceName + "] -> [" + binder + "]");
                        //把剪切板服务替换成我们自己的服务进行驻留
                        if (TARGET_BINDER_SERVICE_NAME.equals(serviceName)) {
                            // Replace the clipboard service so apps can acquire binder
                            CLog.i("replacing clipboard service");
                            // 替换完毕以后这个BinderServiceProxy就是剪切板服务
                            args[1] = new BinderServiceProxy((Binder) args[1],
                                    TARGET_BINDER_SERVICE_DESCRIPTOR, rms, rms::isEnabledFor);
                            //args[2] = true; // Do not supports isolated processes yet
                            clipboardServiceReplaced = true;
                        } else if ("package".equals(serviceName)) {
                            CLog.i("package manager is available");
                            rms.setPackageManager((IBinder) args[1]);
                            packageManagerReady = true;
                        } else if (Context.ACTIVITY_SERVICE.equals(serviceName)) {
                            CLog.i("activity manager is available");
                            activityManagerReady = true;
                            RuntimeManagerService.initContext();
                        }
                        if (activityManagerReady &&
                                packageManagerReady && clipboardServiceReplaced) {
                            //set orig ServiceManager
                            ServiceManager.sServiceManager.setStaticValue(origServiceManager);
                        }
                    }
                    try {
                        return method.invoke(origServiceManager, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                }));

        hookPackageLoad(rms);


//        try {
//        // We want to know if sepolicy patch_boot rules is loaded properly,
//        // so we hook the method even if no modules need to hook system server.
//            // ActivityThread#systemMain() is inlined in Android T
//            // So we also hook ZygoteInit#handleSystemServerProcess()
//            // Call chain:
//            //  ZygoteInit.handleSystemServerProcess()
//            //  SystemServer.run()
//            //    -> SystemServer.createSystemContext()
//            //      -> ActivityThread.systemMain()
//            //        -> ActivityThread.attach(true, 0)
//            //          -> ActivityThread.getSystemContext()
//            //            -> if (mSystemContext == null)
//            //            -> mSystemContext = ContextImpl.createSystemContext()
//            //            -> return mSystemContext
//            //    -> SystemServer.startBootstrapServices()
//            final var called = new boolean[]{false};
//            var hook = new RC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    super.afterHookedMethod(param);
//                    // Both systemMain() and handleSystemServerProcess() are using this callback
//                    if (called[0]) return;
//                    called[0] = true;
//                    RuntimeManagerService.loadedPackages.add(AppConstants.ANDROID);
//                    //hook system service
//                    {
//                        ClassLoader cl = Thread.currentThread().getContextClassLoader();
//                        final String packageName = AppConstants.ANDROID;
//                        // it's actually system_server, but other functions return this as well
//                        final String processName = AppConstants.ANDROID;
//                        AddLoadProcessInfo(packageName,processName,cl,rms);
//                    }
//                    hookPackageLoad(rms);
//                }
//            };
//
//            RposedBridge.hookAllMethods(android.app.ActivityThread.class, "systemMain", hook);
//            RposedBridge.hookAllMethods(ZygoteInit.class, "handleSystemServerProcess", hook);
//            CLog.i(">>>>>>>>>>>> MainStub onSystemServerStart init finish ");
//        }
//        catch (Throwable throwable) {
//            CLog.e("MainStub hook ");
//        }
    }

    /**
     * hook system_service load info
     * 系统服务是一个容器,里面会额外开启多个进程提供对应的功能,比如下面的这些就是其他的进程的 。
     * com.android.providers.settings
     * com.android.server.telecom
     * com.android.networkstack.inprocess
     * com.fingerprints.extension.service
     */
    private static void hookPackageLoad(RuntimeManagerService rms) {
        //hook package load
        try {
            RposedBridge.hookAllMethods(
                    RposedHelpers.findClass("android.app.ActivityThread",
                            Thread.currentThread().getContextClassLoader()),
                    "handleBindApplication", new RC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(RC_MethodHook.MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            android.app.ActivityThread activityThread = (android.app.ActivityThread) param.thisObject;
                            Object appBindData = param.args[0];
                            ApplicationInfo appInfo = ActivityThread.AppBindData.appInfo.getValue(appBindData);
                            if (appInfo == null) return;
                            CompatibilityInfo compatInfo = ActivityThread.AppBindData.compatInfo.getValue(appBindData);
                            ActivityThread.mBoundApplication.setValue(activityThread, appBindData);
                            LoadedApk loadedApk = activityThread.getPackageInfoNoCheck(appInfo, compatInfo);
                            String packageName = appInfo.packageName.equals("android") ? "system" : appInfo.packageName;
                            String processName = ActivityThread.AppBindData.processName.getValue(appBindData);
                            ClassLoader classLoader = loadedApk.getClassLoader();
                            RuntimeManagerService.addPackageLoaderInfo(classLoader);
                            CLog.i(">>>>>>>> handleBindApplication load process " + processName + " " + packageName);

                        }
                    });
            //hook process load
            //when a package is loaded for an existing process, trigger the callbacks as well
            RposedHelpers.findAndHookConstructor(
                    RposedHelpers.findClass("android.app.LoadedApk",
                            Thread.currentThread().getContextClassLoader()),
                    android.app.ActivityThread.class,
                    ApplicationInfo.class, CompatibilityInfo.class, ClassLoader.class,
                    boolean.class, boolean.class, boolean.class,
                    new RC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam callFrame) throws Throwable {
                            super.afterHookedMethod(callFrame);
                            LoadedApk loadedApk = (LoadedApk) callFrame.thisObject;
                            if (!RposedHelpers.getBooleanField(loadedApk, "mIncludeCode")) return;
                            String packageName = loadedApk.getPackageName();
                            // OnePlus magic...
                            if (Log.getStackTraceString(new Throwable()).
                                    contains("android.app.ActivityThread$ApplicationThread.schedulePreload")) {
                                CLog.d("LoadedApk#<init> maybe oneplus's custom opt, skip");
                                return;
                            }
                            //CLog.i("system_service LoadedApk load packageName " + packageName);
                            RuntimeManagerService.addPackageLoaderInfo(loadedApk.getClassLoader());
                        }
                    });
        } catch (Throwable e) {
            CLog.e("hookPackageLoad error " + e, e);
        }
        CLog.i("Runtime Service hookPackageLoad Init finish ! @ ~ ");
    }

    private static Context initContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            android.app.ActivityThread currentActivityThread = (android.app.ActivityThread) currentActivityThreadMethod.invoke(null);
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
            Object applicationContext = createAppContext.invoke(null, currentActivityThread, applicationInfo);
            return (Context) applicationContext;
        } catch (Throwable e) {
            CLog.e(">>>>>>>>>>>>  static get context fail " + e);
        }
        return null;
    }

    /**
     * 当目标Apk启动被调用。
     */
    @SuppressWarnings("unused")
    @Keep
    public static void onAppProcessStart(IBinder tService,
                                         boolean isMainTagApk,
                                         String processName) {
        try {
            CLog.i(">>>>>>>>>>>>> MainStub onAppProcessStart  started!");
            commonInit();
            IBinder service = RuntimeAction.getAppProcessIpcBinder(tService);
            if (service == null) {
                CLog.i("onAppProcessStart not find service binder ");
                return;
            }
            //RuntimeManagerService Runtime服务端实例
            IRuntimeService rms = RuntimeManagerService.Stub.asInterface(service);
            CLog.e(">>>>>>>>>>>>> onAppProcessStart getAppName " + processName);

            //context create
            //时机点过早,ActivityThread.currentOpPackageName()为null
            RposedBridge.hookAllMethods(
                    Class.forName("android.app.ContextImpl"),
                    "createAppContext",
                    new RC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            Context context = (Context) param.getResult();
                            if (isMainTagApk) {
                                //tag apk
                                RuntimeClientInit.RuntimeClientInitStart(context, rms);
                            }
                        }
                    });

        } catch (Throwable e) {
            CLog.i(">>>>>>>>>>>>> MainStub onAppProcessStart error !" + e);
        }
    }
}

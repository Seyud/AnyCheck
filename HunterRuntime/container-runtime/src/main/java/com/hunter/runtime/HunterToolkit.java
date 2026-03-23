package com.hunter.runtime;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.hunter.buildsrc.RunTimeConstants;
import com.hunter.external.extend.ChooseUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * @author Zhenxi on 2022/10/22
 */
public class HunterToolkit {
    private static Context sContext ;
    public static int getSDKVersion() {
        return android.os.Build.VERSION.SDK_INT;
    }

    /**
     * 当前进程名称
     */
    public static String processName = null;

    /**
     * 当成packageName
     */
    public static String packageName = null;

    /**
     * 新增获取Context方法
     */
    public static Context getContext(){
        if(sContext!=null){
            return sContext;
        }
        //Android 9.0以上可以动态内存去查找
        if (getSDKVersion() >= 28) {
            //先尝试查找Application
            ArrayList<Application> ApplicationChoose = ChooseUtils.choose(Application.class);
            if (ApplicationChoose != null && ApplicationChoose.size() >= 1) {
                sContext = ApplicationChoose.get(0).getApplicationContext();
                return sContext;
            }
            //查找Context
            if (sContext == null) {
                ArrayList<Context> ContextChoose = ChooseUtils.choose(Context.class, true);
                if (ContextChoose != null && ContextChoose.size() >= 1) {
                    sContext = ContextChoose.get(0);
                    return sContext;
                }
            }
        }
        //上述都不行在尝试创建
        sContext = getContextInThread();

        return sContext;
    }
    private static Context getContextInThread()  {
        try {
            Log.i(RunTimeConstants.TAG, "start from static method");
//            if (getSDKVersion() >= Build.VERSION_CODES.P) {
//                HiddenAPIEnforcementPolicyUtils.passApiCheck();
//            }
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread", new Class[0]);
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread = currentActivityThreadMethod.invoke(null, new Object[0]);
            Field declaredField = activityThreadClass.getDeclaredField("mBoundApplication");
            declaredField.setAccessible(true);
            Object mBoundApplication = declaredField.get(currentActivityThread);
            Field applicationInfoField = mBoundApplication.getClass().getDeclaredField("info");
            applicationInfoField.setAccessible(true);
            Object applicationInfo = applicationInfoField.get(mBoundApplication);
            Method createAppContext = Class.forName("android.app.ContextImpl").getDeclaredMethod("createAppContext",
                    new Class[]{activityThreadClass, applicationInfo.getClass()});
            createAppContext.setAccessible(true);
            return (Context) createAppContext.invoke(null, new Object[]{currentActivityThread, applicationInfo});
        } catch (Throwable e) {
            Log.e(RunTimeConstants.TAG," getContextInThread error "+e);
        }
        return null;
    }
}

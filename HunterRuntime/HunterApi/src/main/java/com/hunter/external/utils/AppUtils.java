package com.hunter.external.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author com.zhenxi on 2021/6/18
 */
public class AppUtils {


    public static void getStackInfo() {
        try {
            throw new Exception("");
        } catch (Exception e) {
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                CLog.e(  stackTraceElement.getClassName() + "." +
                                stackTraceElement.getMethodName()+" "+stackTraceElement.getMethodName()+"/"+
                                stackTraceElement.getLineNumber());
            }
            CLog.e("-------------------------------");
        }
    }



    public static String getPmsName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Field mPmField = pm.getClass().getDeclaredField("mPM");
            mPmField.setAccessible(true);
            return mPmField.get(pm).getClass().getName();

        } catch (Throwable e) {
            CLog.e("getPmsName error " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取当前进程的名字，一般就是当前app的包名
     *
     * @return 返回进程的名字
     */
    public static String getProcessName(){
        FileInputStream in = null;
        try {
            String fn = "/proc/"+ Process.myPid() +"/cmdline";
            in = new FileInputStream(fn);
            byte[] buffer = new byte[200];
            int len = 0;
            int b;
            while ((b = in.read()) > 0 ) {
                buffer[len++] = (byte) b;
            }
            return new String(buffer, StandardCharsets.UTF_8).trim();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static void getMapInfo(Map<String, String> map) {
        if(map==null){
            CLog.e("getMapInfo->map null");
            return;
        }
        for(Map.Entry<String, String> stringEntry: map.entrySet()){
            CLog.e("Map Key-> "+stringEntry.getKey()+"  Value-> "+stringEntry.getValue());
        }
        CLog.e("---------------------------");
    }

    public static boolean isHaveMethod(Class clazz,String methodName){
        if(clazz == null){
            return false;
        }
        for(Method method:clazz.getDeclaredMethods()){
            if(method.getName().equals(methodName)){
                return true;
            }
        }
        return false;
    }
}

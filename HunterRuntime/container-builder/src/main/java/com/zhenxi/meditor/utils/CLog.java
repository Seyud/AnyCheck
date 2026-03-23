package com.zhenxi.meditor.utils;

public class CLog {
    private static final boolean DEBUG = false;

    public CLog() {
    }

    public static void i(String msg) {
        System.out.println(msg);
    }

    public static void e(String msg) {
        System.err.println(msg);
    }
    public static void e(String msg,Throwable throwable) {
        System.err.println(msg);
        throwable.printStackTrace();
    }
    public static void d(String msg) {
    }
}

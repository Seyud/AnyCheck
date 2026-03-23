package com.zhenxi.meditor.utils;

public class Log {
    private static final boolean DEBUG = false;

    public Log() {
    }

    public static void i(String msg) {
        System.out.println(msg);
    }

    public static void e(String msg) {
        System.err.println(msg);
    }

    public static void d(String msg) {
    }
}

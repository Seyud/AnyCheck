package com.hunter.runtime.app;

import android.app.Application;

import com.hunter.runtime.HunterRuntime;


/**
 * @author zhenxi on 2022/1/7
 *
 * 这个类主要是为了查询内存泄漏和卡顿的
 * 将runtime变成app方便查询内存泄漏问题
 */
public class App extends Application {
    static {
        HunterRuntime.startFromStaticMethod();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}

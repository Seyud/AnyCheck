package com.example.test;

import android.app.Application;
import android.util.Log;

//import com.github.gzuliyujiang.oaid.DeviceIdentifier;

/**
 * @author com.zhenxi on 2022/1/5
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("Zhenxi","测试Application onCreate被调用");
    }
}

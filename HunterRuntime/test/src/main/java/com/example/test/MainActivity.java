package com.example.test;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;



//import com.swift.sandhook.xposedcompat.XposedCompat;


import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;




public class MainActivity extends Activity {

    static {
        System.loadLibrary("testQC");
    }

    public native void inlinehook();

    public native void inlinehook2();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //inlinehook();
        //inlinehook2();



    }

    @Override
    public String toString() {
        return "111";
    }

    public String test(String str){
        return "111111111->"+str;
    }

    public String test2(String str) throws Exception{
        throw new Exception("这是一个异常！");
    }
}
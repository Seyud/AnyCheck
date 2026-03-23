package com.runtime.magisk;

interface IRuntimeService {
    String TestRuntime() ;
    //返回当前调用者的包名
    String getAppName();
}

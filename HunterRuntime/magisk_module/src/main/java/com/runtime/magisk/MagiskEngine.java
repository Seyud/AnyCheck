package com.runtime.magisk;

import android.content.Context;

import androidx.annotation.Keep;

/**
 * @author Zhenxi on 2023/11/25
 * Magisk模块公用Native引擎类
 */
@Keep
public class MagiskEngine {
  public static native void systemServerNativeInit();


}

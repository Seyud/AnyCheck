package com.hunter.runtime;

/**
 * @author Zhenxi on 2023/5/21
 */
public class TestClass {


  static {
      System.loadLibrary("test");
  }
  public static native void test(Object obj);


}

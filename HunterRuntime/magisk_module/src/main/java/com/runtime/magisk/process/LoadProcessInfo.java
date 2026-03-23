package com.runtime.magisk.process;

import androidx.annotation.NonNull;

/**
 * @author Zhenxi on 2023/12/6
 */
public class LoadProcessInfo {
  public String packageName = null;
  public String processName = null;
  public ClassLoader classLoader = null;

  @NonNull
  @Override
  public String toString() {
    return "LoadProcessInfo{" +
            "packageName='" + packageName + '\'' +
            ", processName='" + processName + '\'' +
            '}';
  }
}

package com.hunter.runtime;

import static com.hunter.external.utils.FileUtils.makeSureDirExist;

import android.content.Context;

import com.hunter.buildsrc.RunTimeConstants;

import java.io.File;

/**
 * @author Zhenxi on 2022/10/16
 */
public class HunterEnv {

  public static File HunterResourceDir(Context context) {
    return makeSureDirExist(
            context.getDir("HunterResource", Context.MODE_PRIVATE));
  }

  public static File HunterTestDir(Context context) {
    return makeSureDirExist(
            context.getDir("Test", Context.MODE_PRIVATE));
  }




}

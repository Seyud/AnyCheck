package com.hunter.runtime;


import android.content.Context;




import java.io.File;


public class RuntimeEnvironment {

    private static File RUNTIME_RES_FILE = null;
    /**
     * zhenxi资源目录，存储和虚拟环境无关的，zhenxi框架资源。
     * 指向:
     * /data/user/0/xxx/app_zhenxi_resource/
     * 这个文件应该是随机的,防止防御方得到apk包以后直接读取资源文件。
     *
     * @return Runtime运行框架资源
     */
//    public static File zhenxiResourceDir() {
//        if(RUNTIME_RES_FILE == null) {
//            File dataDir = new File("/data/data/" + RuntimeToolKit.getContext().getPackageName() + "/");
//            File file = MyFileUtils.findFile(dataDir.getPath(), RunTimeConstants.Runtime_ORIGIN_APK_NAME);
//            //说明没有创建过,则随机产一个字符串路径
//            if (file == null) {
//                RUNTIME_RES_FILE = new File(dataDir, RandomUtils.getRandomString(5,10));
//            }else {
//                //说明已经创建过
//                RUNTIME_RES_FILE = new File(dataDir, Objects.requireNonNull(file.getParentFile()).getName());
//            }
//        }
//        return makeSureDirExist(RUNTIME_RES_FILE);
//    }
//
//    public static File zhenxiFingerprint() {
//        return makeSureDirExist(new File("/data/data/" + RuntimeToolKit.getContext().getPackageName() + "/fingerprint/"));
//    }
//
//    public static File multiUserConfigFile() {
//        return new File(zhenxiResourceDir(), RunTimeConstants.MULTIUSER_CONFIG_PROPERTIES);
//    }
//
//    public static File zhenxiConfigFile() {
//        return new File(zhenxiResourceDir(), RunTimeConstants.RUNTIME_CONFIG_PROPERTIES);
//    }
//
//    public static File originApkDir() {
//        return new File(zhenxiResourceDir(), RunTimeConstants.Runtime_ORIGIN_APK_NAME);
//    }
//
//
//
//    public static void releaseRuntimeResources(Context context) {
//        if(TestRuntime.isRePackage) {
//            CLog.i("runtime random res dir name -> [" + RuntimeEnvironment.zhenxiResourceDir() + "]");
//            //这个文件需要加白
//            releaseAssetResource(RunTimeConstants.RUNTIME_CONFIG_PROPERTIES, context);
//            releaseAssetResource(RunTimeConstants.Runtime_ORIGIN_APK_NAME, context);
//        }
//    }
//
//
//    private static void releaseAssetResource(String name, Context context) {
//        File file = zhenxiResourceDir();
//        if (!file.exists()) {
//            boolean mkdirs = file.mkdirs();
//            if (!mkdirs) {
//                CLog.i(">>>>>>>>>>> create runtime dir file fail " + file.getPath());
//            }
//        }
//        //生成的文件
//        File copyFile = new File(file, name);
//        releaseAssetResource(context, copyFile);
//    }

//    private static void releaseAssetResource(Context context, File copyFile) {
//        //[主线程]或者[文件不存在]才进行拷贝。
//        //很多app第一个启动的进程不一定是主线程
//        if (context.getPackageName()
//                .equals(RuntimeToolKit.processName) || !copyFile.exists()) {
//            CLog.i("start copy assets file  -> " + copyFile.getPath() + " "
//                    + copyFile.exists() + " assets name -> " + copyFile.getName());
//            AssetManager assets = RuntimeToolKit.getContext().getAssets();
//            InputStream inputStream = null;
//            FileOutputStream fileOutputStream = null;
//            try {
//                try {
//                    inputStream = assets.open(copyFile.getName());
//                } catch (Throwable fie) {
//                    CLog.e("releaseAssetResource assets.open(name) error  -> " + fie.getMessage());
//                    return;
//                }
//                CLog.i("copy assets resource file path -> " + copyFile);
//                fileOutputStream = new FileOutputStream(copyFile);
//                IOUtils.copy(inputStream, fileOutputStream);
//                if (copyFile.exists()) {
//                    CLog.e(">>>>>>>>> run time copy res file success " + copyFile.getPath());
//                } else {
//                    CLog.e("!!!!!!!! run time copy res file fail !!!!!!!!! " + copyFile.getPath());
//                }
//            } catch (Throwable e) {
//                Log.e(TAG, "copy assets resource failed!!  " + e.getMessage(), e);
//            } finally {
//                try {
//                    if (fileOutputStream != null) {
//                        fileOutputStream.close();
//                    }
//                    if (inputStream != null) {
//                        inputStream.close();
//                    }
//                } catch (IOException ignored) {
//
//                }
//            }
//        }
//    }

    public static File makeSureDirExist(File file) {
        if (file.getPath().equals("")) {
            return file;
        }
        if (file.exists() && file.isFile()) {
            return file;
        }
        //CLog.i(">>>>>>>>>>>>>>> makeSureDirExist create dir "+file.getPath());
        boolean isSuccess = file.mkdirs();
        return file;
    }


//    public static File nativeCacheDir() {
//        return makeSureDirExist(new File(zhenxiResourceDir(), RunTimeConstants.nativeCacheDir));
//    }

//    public static File sandHookCacheDir(Context context) {
//        return makeSureDirExist(new File(zhenxiResourceDir(context), RunTimeConstants.sandHookCache));
//    }

//    public static File modulesDexDir() {
//        return makeSureDirExist(new File(zhenxiResourceDir(), RunTimeConstants.RUNTIME_MODULES_DEX_DIR));
//    }

//    public static File originAPKSignatureFile() {
//        return new File(zhenxiResourceDir(), "signature.ini");
//    }

    /**
     * 返回/data/data/包名/virtual_devices/
     */
    public static File envMockBaseDir() {
        return HunterToolkit.getContext().getDir("virtual_devices", Context.MODE_PRIVATE);
    }

    /**
     * 返回/data/data/包名/virtual_devices/当前分身
     */
    public static File envMockDir() {
        return makeSureDirExist(new File(envMockBaseDir(), "DEF_USER"));
    }

    /**
     * @return 内存卡模拟目录
     */
    public static File envMockSdcard() {
        return makeSureDirExist(new File(envMockDir(), "sdcard"));
    }

    /**
     * @return 模拟 /data/data/pkg/virtual_devices/当前分身/data
     */
    public static File envMockData() {
        return makeSureDirExist(new File(envMockDir(), "data"));
    }

    /**
     * 保存我们自己的东西,不和宿主App本身的data冲突,只有在保存我们自己的东西的时候才用这个方法。
     */
    public static File envRuntimeMyDataDir() {
        return makeSureDirExist(new File(envMockDir(), "runtime_data"));
    }

    /**
     * 保存临时maps的目录
     */
    public static File envRuntimeTempMapCacheDir() {
        return makeSureDirExist(new File(envRuntimeMyDataDir().getPath() + "/temp_map"));
    }

    /**
     * 保存native cache的目录
     */
    public static File envRuntimeNativeCacheDir() {
        return makeSureDirExist(new File(envRuntimeMyDataDir().getPath() + "/native_cache"));
    }

    /**
     * 获取IO重定向以后保存fingerprint路径
     */
    public static String envRuntimeFingerPrintPathDir() {
        return makeSureDirExist(new File(envRuntimeMyDataDir().getPath() + "/mock_fingerprint")).getPath();
    }

    /**
     * 指纹下的popen相关路径
     */
    public static String envRuntimeFingerPrintPathPopenDir() {
        return makeSureDirExist(new File(envRuntimeFingerPrintPathDir()+"/mock_popen")).getPath();
    }



    /**
     * 获取IO重定向以后保存临时文件的目录
     */
    public static String envRuntimeTempPathDir() {
        return makeSureDirExist(new File(envRuntimeMyDataDir().getPath() + "/runtime_temp")).getPath();
    }

    /**
     * 保存mmkv的目录
     */
    public static File envRuntimeMMKVCacheDir(String fileName) {
        return makeSureDirExist(new File(new File(envRuntimeMyDataDir().getPath() + "/mmkv_cache"), fileName));
    }


    /**
     * @return 模拟 /data/user_de/pkg
     */
    public static File envMockData_de() {
        return makeSureDirExist(new File(envMockDir(), "user_de"));
    }

    /**
     * 获取sd卡白名单目录
     */
    public static String whiteSdCardDir() {
        return "/sdcard/zhenxi_white_dir/" + HunterRuntime.packageName + "/";
    }


    public static File envFingerPrintWifiFile() {
        return new File(envRuntimeFingerPrintPathDir(), "wifi.db");
    }

    public static File envFingerPrintBluetoothFile() {
        return new File(envRuntimeFingerPrintPathDir(), "bluetooth.db");
    }

    public static File envFingerPrintLocationFile() {
        return new File(envRuntimeFingerPrintPathDir(), "location.db");
    }

    public static File envFingerPrintBaseInfoFile() {
        return new File(envRuntimeFingerPrintPathDir(), "baseInfo.db");
    }
    public static File envFingerPrintNativeInfoFile() {
        return new File(envRuntimeFingerPrintPathDir(), "nativeInfo.db");
    }
    public static File envFingerPrintAppsFile() {
        return new File(envRuntimeFingerPrintPathDir(), "applications.db");
    }

    public static File envFingerPrintMsaFile() {
        return new File(envRuntimeFingerPrintPathDir(), "oaid.db");
    }
    public static File envFingerPrintWebViewFile() {
        return new File(envRuntimeFingerPrintPathDir(), "webView.db");
    }
    public static File envFingerPrintFile(String fileName) {
        return new File(envRuntimeFingerPrintPathDir(), fileName);
    }

    public static File envTempFile(String fileName) {
        return new File(envRuntimeTempPathDir(), fileName);
    }

    public static File envRuntimeFingerPrintPathPopenDir(String fileName) {
        return new File(envRuntimeFingerPrintPathPopenDir(), fileName);
    }


}

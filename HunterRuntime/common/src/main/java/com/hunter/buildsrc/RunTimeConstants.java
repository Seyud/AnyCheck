package com.hunter.buildsrc;

public class RunTimeConstants {




    public static final String TAG = "Zhenxi";


    public static final String Runtime_ORIGIN_APK_NAME = "abcd.apk";



    public static final String MULTIUSER_CONFIG_PROPERTIES = "MultiUserConfig.properties";

    public static final String multiUserIdKey = "multiUserIdKey";

    public static final String multiUserListKey = "multiUserListKey";

    public static final String needRemoveUserListKey = "multiUserNeedRemoveKey";

    public static String RUNTIME_ENGINE_RESOURCE_APK_NAME = "runtime-engine.apk";

    public static String RUNTIME_MANAGER_APK_NAME = "com.zhenxi.container_manager";


    public static String manifestFileName = "AndroidManifest.xml";
    public static String nativeCacheDir = "nativeCache";

    public static boolean devBuild = false;



    public static final String RUNTIME_CONFIG_PROPERTIES = "RunTimeConfig.properties";

    public static final String RUNTIME_CONSTANTS_PREFIX = "runtime_constant.";

    public static final String RuntimeApplicationClassName = "com.setting.runtime.RuntimeApplication";

    public static final String SimpleRuntimeApplicationClassName = "com.setting.runtime.SimpleRuntimeApplication";
    public static final String RuntimeComponentFactoryClassName = "com.setting.runtime.RuntimeComponentFactory";

    public static final String RuntimeZygotePreloadClassName = "com.setting.runtime.RuntimeApplicationZygote";

    public static String RuntimePrefix = "Runtime_";

    public static String KEY_ORIGIN_PKG_NAME = "originPackageName";
    public static String KEY_ORIGIN_APPLICATION_NAME = "originApplicationName";
    public static String KEY_zhenxi_BUILD_SERIAL = RuntimePrefix + "serialNo";
    public static String KEY_zhenxi_BUILD_TIMESTAMP = RuntimePrefix + "buildTimestamp";

    public static String zhenxiDefaultApkSignatureKey = "runtime_keySign";

    public static String RUNTIME_MODULES_DEX_DIR = "runtime_modules_dex";


    public static final String MODULES_LIST_FILE = "modules.list";


    public static final String ENABLED_MODULES_LIST_FILE = "enabled_modules.list";

    public static final String XPOSED_MODULE_META_FLAG = "xposedmodule";


    public static final String XPOSED_VIRTUAL_MOUDEL = "virtualEnvModel";

    public static final String HOT_XPOSED_MODULE_META_FLAG = "hotModule";


    public static final String runtime_MODULE_META_FLAG = "for_runtime_apps";


    public static final String HOT_MODULE_SERVER_URL = "hotModuleServerUrl";


    public static final String IS_LOAD_FRIDA_GADGET = "is_frida_embed";

    public static final String ORIG_APK_SIGN = "orgApkSignStr";
}

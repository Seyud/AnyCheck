package com.hunter.external.extend;




import static com.hunter.api.rposed.RposedHelpers.callMethod;
import static com.hunter.api.rposed.RposedHelpers.callStaticMethod;
import static com.hunter.api.rposed.RposedHelpers.findAndHookConstructor;
import static com.hunter.api.rposed.RposedHelpers.findAndHookMethod;
import static com.hunter.api.rposed.RposedHelpers.findClass;
import static com.hunter.api.rposed.RposedHelpers.getObjectField;
import static com.hunter.api.rposed.RposedHelpers.newInstance;
import static com.hunter.api.rposed.RposedHelpers.setObjectField;

import android.net.http.SslError;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;


import com.hunter.api.rposed.RC_MethodHook;
import com.hunter.api.rposed.RC_MethodReplacement;
import com.hunter.api.rposed.RposedBridge;
import com.hunter.buildsrc.RunTimeConstants;
import com.hunter.external.utils.ThreadUtils;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.HostNameResolver;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import dalvik.system.DexFile;

/**
 * @author zhenxi on 2021/6/14
 * 主要用于抓包验证
 */
public class JustTrushMePlush {


    private static List<String> classNameList = new ArrayList<String>();
    //存放 全部类的 集合
    public static volatile List<Class<?>> mClassList = new ArrayList<>();

    private static ClassLoader mLoader = null;

    //OkHttp里面的 类
    private static Class<?> OkHttpBuilder = null;

    private static Class<?> OkHttpClient = null;

    /**
     * @param mLoader 当前进程的Classloader
     * @param TryHookConfusion  尝试遍历Class的方式去查找okHttp相关类信息,
     * 为了解决okhttp混淆以后Hook失败的问题,目前只针对okhttp开头的类进行遍历,不然会很耗时。
     */
    public static void StartJustTrushMePlush(ClassLoader mLoader,boolean TryHookConfusion){
        JustTrushMePlush.mLoader=mLoader;

        //JustTrushMe 原方法
        try {
            processOkHttp(mLoader);
            processHttpClientAndroidLib(mLoader);
            processXutils(mLoader);
            JustMePlush();

            if(TryHookConfusion){
                getAllClassNameAndInit();
            }
        } catch (Throwable e) {
            JustTrushMePlush.mLoader = null;
            Log.e(RunTimeConstants.TAG,"StartJustTrushMePlush error "+e.getMessage());
            Log.e(RunTimeConstants.TAG,Log.getStackTraceString(e));
        }
    }
    private static void getDexFileClassName(DexFile dexFile) {
        if(dexFile!=null) {
            //获取df中的元素  这里包含了所有可执行的类名 该类名包含了包名+类名的方式
            Enumeration<String> enumeration = dexFile.entries();
            while (enumeration.hasMoreElements()) {//遍历
                String className = enumeration.nextElement();
                //todo 此处可优化，目前只是针对了Okhttp开头的进行了遍历 2021年6月14日20:33:27
                if (className.contains("okhttp")) {//在当前所有可执行的类里面查找包含有该包名的所有类
                    classNameList.add(className);
                }
            }
        }
    }


    private static synchronized void getAllClassNameAndInit() {
        classNameList.clear();
        try {

            ThreadUtils.runOnNonUIThread(() -> {
                try {
                    //系统的 classloader是 Pathclassloader需要 拿到他的 父类 BaseClassloader才有 pathList
                    Field pathListField = Objects.requireNonNull(mLoader.getClass().getSuperclass()).getDeclaredField("pathList");
                    pathListField.setAccessible(true);
                    Object dexPathList = pathListField.get(mLoader);
                    Field dexElementsField = dexPathList.getClass().getDeclaredField("dexElements");
                    dexElementsField.setAccessible(true);
                    Object[] dexElements = (Object[]) dexElementsField.get(dexPathList);
                    for (Object dexElement : dexElements) {
                        Field dexFileField = dexElement.getClass().getDeclaredField("dexFile");
                        dexFileField.setAccessible(true);
                        DexFile dexFile = (DexFile) dexFileField.get(dexElement);
                        getDexFileClassName(dexFile);
                    }
                    HookOkHttpClient();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    private static boolean isBuilder(Class ccc) {

        try {
            int ListTypeCount = 0;
            int FinalTypeCount = 0;
            Field[] fields = ccc.getDeclaredFields();
            for (Field field : fields) {
                String type = field.getType().getName();
                //四个 集合
                if (type.contains(List.class.getName())) {
                    ListTypeCount++;
                }
                //2 个 为 final类型
                if (type.contains(List.class.getName()) && Modifier.isFinal(field.getModifiers())) {
                    FinalTypeCount++;
                }
            }

            //四个 List 两个 2 final  并且 包含父类名字
            if (ListTypeCount == 4 && FinalTypeCount == 2 && ccc.getName().contains(OkHttpClient.getName())) {
                return true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * 混淆 以后 获取  集合并添加 拦截器的方法
     */
    private static void getBuilder() {
        try {
            if (OkHttpClient == null) {
                for(Class c:mClassList){
                    Log.e(RunTimeConstants.TAG,"StartJustTrushMePlush TryHookConfusion error  "+c.getName());
                }
                return;
            }
            //开始查找 build
            for (Class builder : mClassList) {
                if (isBuilder(builder)) {
                    OkHttpBuilder = builder;
                }
            }
            if(OkHttpBuilder==null) {
                for(Class c:mClassList){
                    Log.e(RunTimeConstants.TAG,"StartJustTrushMePlush TryHookConfusion error  "+c.getName());
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    private static Method getSslSocketFactoryMethod(Class sslSocketFactoryClass, Class x509TrustManagerClass) {
        Method[] declaredMethods = OkHttpBuilder.getDeclaredMethods();
        try {
            for (int i = 0; i < declaredMethods.length; i++) {
                declaredMethods[i].setAccessible(true);
                Class<?>[] parameterTypes = declaredMethods[i].getParameterTypes();
                if (parameterTypes.length == 2) {
                    if (parameterTypes[0].getName().equals(sslSocketFactoryClass.getName()) &&
                            parameterTypes[1].getName().equals(x509TrustManagerClass.getName())) {
                        return declaredMethods[i];
                    }
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Method getSslSocketFactoryMethodOneType(Class sslSocketFactoryClass) {
        Method[] declaredMethods = OkHttpBuilder.getDeclaredMethods();
        try {
            for (int i = 0; i < declaredMethods.length; i++) {
                declaredMethods[i].setAccessible(true);
                Class<?>[] parameterTypes = declaredMethods[i].getParameterTypes();
                if (parameterTypes.length == 1) {
                    if (parameterTypes[0].getName().equals(sslSocketFactoryClass.getName())) {
                        return declaredMethods[i];
                    }
                }

            }


        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Method getSslSocketFactoryMethodOneTypeForClient(Class sslSocketFactoryClass) {
        Method[] declaredMethods = OkHttpClient.getDeclaredMethods();
        try {
            for (int i = 0; i < declaredMethods.length; i++) {
                declaredMethods[i].setAccessible(true);
                Class<?>[] parameterTypes = declaredMethods[i].getParameterTypes();
                if (parameterTypes.length == 1) {
                    if (parameterTypes[0].getName().equals(sslSocketFactoryClass.getName())) {
                        return declaredMethods[i];
                    }
                }
//                if(declaredMethods[i].getName().equals("a")){
//                    Log.e(TAG,"参数1 "+parameterTypes[0].getName());
//                    Log.e(TAG,"参数1 "+sslSocketFactoryClass.getName());
//
//                }
            }

            //ClassUtils.getClassMethodInfo(OkHttpClient);

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Method getCertificatePinnerCheckMethod(Class certificatePinnerClass) {
        Method[] declaredMethods = certificatePinnerClass.getDeclaredMethods();
        for (Method method : declaredMethods) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 2) {
                if (parameterTypes[0].getName().equals(String.class.getName()) && parameterTypes[1].getName().equals(List.class.getName())) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Method getOkHttpCertificatePinnerCheckMethod(Class certificatePinnerClass) {
        Method[] declaredMethods = OkHttpBuilder.getDeclaredMethods();
        for (Method method : declaredMethods) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1) {
                if (parameterTypes[0].getName().equals(certificatePinnerClass.getName())) {
                    return method;
                }
            }
        }
        return null;
    }


    private static Class OkHostnameVerifierClass() {

//                public final class OkHostnameVerifier implements HostnameVerifier {
//                public static final OkHostnameVerifier INSTANCE = new OkHostnameVerifier();

//                private static final int ALT_DNS_NAME = 2;
//                private static final int ALT_IPA_NAME = 7;

        try {
            Class<?> HostnameVerifier = Class.forName("javax.net.ssl.HostnameVerifier", true, mLoader);


            for (Class mClass : mClassList) {
                int privateCount = 0;
                if (mClass.getInterfaces().length == 1 && mClass.getInterfaces()[0].getName().equals("javax.net.ssl.HostnameVerifier")) {

                    //接口类型 是 HostnameVerifier 并且是 final类型
                    if (Modifier.isFinal(mClass.getModifiers())) {
                        Field[] declaredFields = mClass.getDeclaredFields();



                        // 三个变量都是 final和 static类型
                        if (declaredFields.length == 3) {

                            for (Field field : declaredFields) {
                                if (Modifier.isPrivate(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                                    privateCount++;
                                }
                            }
                            if (privateCount == 2) {
                                return mClass;
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Method gethostnameVerifierMethod(Class HostnameVerifierClass) {
        Method[] declaredMethods = OkHttpBuilder.getDeclaredMethods();
        for (Method declaredMethod : declaredMethods) {
            declaredMethod.setAccessible(true);
            Class<?>[] parameterTypes = declaredMethod.getParameterTypes();
            if (parameterTypes.length == 1) {
                if (parameterTypes[0].getName().equals(HostnameVerifierClass.getName())) {
                    return declaredMethod;
                }
            }
        }
        return null;
    }


    private static Class getCertificatePinnerClass() {
//            public final class CertificatePinner {
//            public static final CertificatePinner DEFAULT = (new CertificatePinner.Builder()).build();
//            private final Set<CertificatePinner.Pin> pins;
//            @Nullable
//            private final CertificateChainCleaner certificateChainCleaner;

        //本身是 final类型  三个变量 都是final类型
        //两个是 private 一个是 pubulic
        // 有一个遍历的类型是 set
        for (Class mClass : mClassList) {
            int privateCount = 0;
            int publicCount = 0;
            int SetTypeCount = 0;

            Field[] declaredFields = mClass.getDeclaredFields();
            //长度 是 3 本身是 final类型
            if (declaredFields.length == 3 && Modifier.isFinal(mClass.getModifiers())) {
                for (Field field : declaredFields) {
                    //私有 并且是 final类型
                    if (Modifier.isFinal(field.getModifiers()) && Modifier.isPrivate(field.getModifiers())) {
                        privateCount++;
                        if (field.getType().getName().equals(Set.class.getName())) {
                            SetTypeCount++;
                        }
                    }
                    if (Modifier.isFinal(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                        publicCount++;
                    }
                }
                if (publicCount == 1 && SetTypeCount == 1 && privateCount == 2) {
                    return mClass;
                }
            }
        }
        return null;
    }

    private static   Method getHostnameVerifierVerifyMethod(Class<?> hostnameVerifier) {
        Method[] declaredMethods = null;
        try {
            declaredMethods = hostnameVerifier.getDeclaredMethods();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        for (Method method : declaredMethods) {
            if (method.getParameterTypes().length == 2) {
                if (method.getParameterTypes()[0].getName().equals(String.class.getName()) &&
                        method.getParameterTypes()[1].getName().equals(SSLSession.class.getName())) {
                    return method;
                }
            }
        }
        return null;
    }
    /**
     * 主要Hook这几个方法
     * <p>
     * //证书检测
     * builder.sslSocketFactory()
     * //域名验证
     * .hostnameVerifier()
     * //证书锁定
     * .certificatePinner()
     */
    private static void HookOkHttpClient() {
        try {
            initAllClass();
            getClientClass();
            getBuilder();

            //方法 1   证书检测   2个 参数类型
            if (OkHttpBuilder != null) {
                Class SSLSocketFactoryClass = Class.forName("javax.net.ssl.SSLSocketFactory");
                Class X509TrustManagerClass = Class.forName("javax.net.ssl.X509TrustManager");


                //先拿到 参数类型的 类
                Method SslSocketFactoryMethod = getSslSocketFactoryMethod(SSLSocketFactoryClass, X509TrustManagerClass);
                if (SslSocketFactoryMethod != null) {
                    //需要先拿到方法名字
                    try {
                        findAndHookMethod(OkHttpBuilder, SslSocketFactoryMethod.getName(),
                                SSLSocketFactoryClass,
                                X509TrustManagerClass,
                                new RC_MethodHook() {

                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        super.beforeHookedMethod(param);
                                        param.args[0] = getEmptySSLFactory();
                                        param.args[1] = new MyX509TrustManager();
                                    }
                                });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }

                }


                Method sslSocketFactoryMethodOneType = getSslSocketFactoryMethodOneType(SSLSocketFactoryClass);
                //方法 2  证书检测   1个 参数类型
                if (sslSocketFactoryMethodOneType != null) {
                    //需要先拿到方法名字
                    try {
                        findAndHookMethod(OkHttpBuilder, sslSocketFactoryMethodOneType.getName(),
                                SSLSocketFactoryClass,
                                new RC_MethodHook() {

                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        super.beforeHookedMethod(param);
                                        param.args[0] = getEmptySSLFactory();
                                    }
                                });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }


                //这两个是为了防止魔改的okHttp
                Method sslSocketFactoryMethodOneTypeforClient = getSslSocketFactoryMethodOneTypeForClient(X509TrustManagerClass);
                //方法 2  证书检测   1个 参数类型
                if (sslSocketFactoryMethodOneTypeforClient != null) {
                    //需要先拿到方法名字
                    try {
                        findAndHookMethod(OkHttpClient, sslSocketFactoryMethodOneTypeforClient.getName(),
                                SSLSocketFactoryClass,
                                new RC_MethodHook() {

                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        super.beforeHookedMethod(param);
                                        param.args[0] = getEmptySSLFactory();
                                    }
                                });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                } else {
                }


                //域名 验证
                Class HostnameVerifierClass = Class.forName("javax.net.ssl.HostnameVerifier");

                Method hostnameVerifierMethod = gethostnameVerifierMethod(HostnameVerifierClass);
                if (hostnameVerifierMethod != null) {
                    //需要先拿到方法名字
                    try {
                        findAndHookMethod(OkHttpBuilder, hostnameVerifierMethod.getName(),
                                HostnameVerifierClass,
                                new RC_MethodHook() {

                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        super.beforeHookedMethod(param);
                                        param.args[0] = new MyHostnameVerifier();
                                    }
                                });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }


                //证书 锁定
                Class CertificatePinnerClass = getCertificatePinnerClass();
                if (CertificatePinnerClass != null) {


                    Method CertificatePinnerCheckMethod = getCertificatePinnerCheckMethod(CertificatePinnerClass);
                    Method okHttpCertificatePinnerCheckMethod = getOkHttpCertificatePinnerCheckMethod(CertificatePinnerClass);

                    if (CertificatePinnerCheckMethod != null) {


                        try {
                            findAndHookMethod(CertificatePinnerClass,
                                    CertificatePinnerCheckMethod.getName(),
                                    String.class,
                                    List.class,
                                    new RC_MethodReplacement() {

                                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                                            return null;
                                        }
                                    });
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }

                        try {
                            //将添加的全部证书验证都删掉
                            RposedBridge.hookAllConstructors(CertificatePinnerClass, new RC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    super.afterHookedMethod(param);
                                    Set pins = (Set)getObjectField(param.thisObject, "pins");
                                    pins.clear();
                                }
                            });
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    if (okHttpCertificatePinnerCheckMethod != null) {
                        try {
                            findAndHookMethod(OkHttpBuilder,
                                    okHttpCertificatePinnerCheckMethod.getName(),
                                    CertificatePinnerClass,
                                    new RC_MethodReplacement() {
                                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                                            return null;
                                        }
                                    });
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }


                Class OkHostnameVerifierClass = OkHostnameVerifierClass();
                if (OkHostnameVerifierClass != null) {
                    try {

                        //这个 有几率为 null
                        Class<?> HostnameVerifier = Class.forName("javax.net.ssl.HostnameVerifier", true, mLoader);

                        Method hostnameVerifierVerifyMethod = getHostnameVerifierVerifyMethod(HostnameVerifier);
                        if (hostnameVerifierVerifyMethod != null) {

                            try {
                                findAndHookMethod(OkHostnameVerifierClass,
                                        hostnameVerifierVerifyMethod.getName(),
                                        String.class,
                                        SSLSession.class,
                                        new RC_MethodReplacement() {

                                            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {

                                                return true;
                                            }
                                        });
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                            try {
                                findAndHookMethod(OkHostnameVerifierClass,
                                        hostnameVerifierVerifyMethod.getName(),
                                        String.class,
                                        X509Certificate.class,
                                        new RC_MethodReplacement() {

                                            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                                                return true;
                                            }
                                        });
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }


            }
        } catch (Throwable e) {
            e.printStackTrace();
        }


    }
    private static boolean isClient(Class<?> mClass) {
        try {
            int typeCount = 0;
            int staticCount = 0;

            //getDeclaredFields 是个 获取 全部的
            Field[] fields = mClass.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                String type = field.getType().getName();

                //6个 集合 6个final 特征
                if (type.contains(List.class.getName()) && Modifier.isFinal(field.getModifiers())) {
                    //Log.e(TAG," 复合 规则 该 Field是      " + field.getName() + " ");
                    typeCount++;
                    if(Modifier.isStatic(field.getModifiers())){
                        staticCount++;
                    }
                }
            }

            if (typeCount == 6 && staticCount==2) {
                return true;
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 获取 ClientCLass的方法
     */
    private static void getClientClass() {
        try {
            if (mClassList.size() == 0) {
                return;
            }
            for (Class mClient : mClassList) {
                //判断 集合 个数 先拿到 四个集合 可以 拿到 Client
                if (isClient(mClient)) {
                    OkHttpClient = mClient;
                    return;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    /**
     * 初始化 需要的 class的 方法
     */
    private static void initAllClass() {

        try {
            for (String path : classNameList) {
                //首先进行初始化
                try {
                    Class<?> aClass = Class.forName(path, false, mLoader);
                    if(aClass!=null) {
                        mClassList.add(aClass);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }

            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    private static void processOkHttp(ClassLoader classLoader) {



        /* hooking OKHTTP by SQUAREUP */
        /* com/squareup/okhttp/CertificatePinner.java available online @ https://github.com/square/okhttp/blob/master/okhttp/src/main/java/com/squareup/okhttp/CertificatePinner.java */
        /* public void check(String hostname, List<Certificate> peerCertificates) throws SSLPeerUnverifiedException{}*/
        /* Either returns true or a exception so blanket return true */
        /* Tested against version 2.5 */

        try {
            classLoader.loadClass("com.squareup.okhttp.CertificatePinner");
            findAndHookMethod("com.squareup.okhttp.CertificatePinner",
                    classLoader,
                    "check",
                    String.class,
                    List.class,
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            return true;
                        }
                    });
        } catch (Throwable e) {
            // pass
        }

        //https://github.com/square/okhttp/blob/parent-3.0.1/okhttp/src/main/java/okhttp3/CertificatePinner.java#L144

        try {
            classLoader.loadClass("okhttp3.CertificatePinner");
            findAndHookMethod("okhttp3.CertificatePinner",
                    classLoader,
                    "check",
                    String.class,
                    List.class,
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            return null;
                        }
                    });
        } catch (Throwable e) {
            // pass
        }

        //https://github.com/square/okhttp/blob/parent-3.0.1/okhttp/src/main/java/okhttp3/internal/tls/OkHostnameVerifier.java
        try {
            classLoader.loadClass("okhttp3.internal.tls.OkHostnameVerifier");
            findAndHookMethod("okhttp3.internal.tls.OkHostnameVerifier",
                    classLoader,
                    "verify",
                    String.class,
                    SSLSession.class,
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            return true;
                        }
                    });
        } catch (ClassNotFoundException e) {
            // pass
        }

        //https://github.com/square/okhttp/blob/parent-3.0.1/okhttp/src/main/java/okhttp3/internal/tls/OkHostnameVerifier.java
        try {
            classLoader.loadClass("okhttp3.internal.tls.OkHostnameVerifier");
            findAndHookMethod("okhttp3.internal.tls.OkHostnameVerifier",
                    classLoader,
                    "verify",
                    String.class,
                    X509Certificate.class,
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            return true;
                        }
                    });
        } catch (Throwable e) {
            // pass
        }

        //https://github.com/square/okhttp/blob/okhttp_4.2.x/okhttp/src/main/java/okhttp3/CertificatePinner.kt

        try {
            classLoader.loadClass("okhttp3.CertificatePinner");
            findAndHookMethod("okhttp3.CertificatePinner",
                    classLoader,
                    "check$okhttp",
                    String.class,
                    "kotlin.jvm.functions.Function0",
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            return null;
                        }
                    });
        } catch (Throwable e) {
            // pass
        }
    }




    public static class MyX509TrustManager implements X509TrustManager {


        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }


        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }


        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    public static class MyHostnameVerifier implements HostnameVerifier {


        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    public static class ImSureItsLegitHostnameVerifier implements HostnameVerifier {


        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    public static class ImSureItsLegitTrustManager implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }


        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }


        public List<X509Certificate> checkServerTrusted(X509Certificate[] chain, String authType, String host) throws CertificateException {
            ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
            return list;
        }


        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }


    public static TrustManager tm = new X509TrustManager() {

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };
    /* This class creates a SSLSocket that trusts everyone. */
    public static class TrustAllSSLSocketFactory extends SSLSocketFactory {

        SSLContext sslContext = SSLContext.getInstance("TLS");

        public TrustAllSSLSocketFactory(KeyStore truststore) throws
                NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);

            sslContext.init(null, new TrustManager[]{tm}, null);
        }


        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }


        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

    class MySSLSocketFactory extends javax.net.ssl.SSLSocketFactory {


        public String[] getDefaultCipherSuites() {
            return new String[0];
        }


        public String[] getSupportedCipherSuites() {
            return new String[0];
        }


        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return null;
        }


        public Socket createSocket(String host, int port) throws IOException {
            return null;
        }


        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return null;
        }


        public Socket createSocket(InetAddress host, int port) throws IOException {
            return null;
        }


        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return null;
        }
    }



    public static javax.net.ssl.SSLSocketFactory getEmptySSLFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new ImSureItsLegitTrustManager()}, null);
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            return null;
        }
    }
    private static void processHttpClientAndroidLib(ClassLoader classLoader) {
        /* httpclientandroidlib Hooks */
        /* public final void verify(String host, String[] cns, String[] subjectAlts, boolean strictWithSubDomains) throws SSLException */

        try {
            classLoader.loadClass("ch.boye.httpclientandroidlib.conn.ssl.AbstractVerifier");
            findAndHookMethod("ch.boye.httpclientandroidlib.conn.ssl.AbstractVerifier", classLoader, "verify",
                    String.class, String[].class, String[].class, boolean.class,
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            return null;
                        }
                    });
        } catch (Throwable e) {
            // pass
        }
    }

    private static void processXutils(ClassLoader classLoader) {
        try {
            classLoader.loadClass("org.xutils.http.RequestParams");
            findAndHookMethod("org.xutils.http.RequestParams", classLoader, "setSslSocketFactory", javax.net.ssl.SSLSocketFactory.class, new RC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.args[0] = getEmptySSLFactory();
                }
            });
            findAndHookMethod("org.xutils.http.RequestParams", classLoader, "setHostnameVerifier", HostnameVerifier.class, new RC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.args[0] = new ImSureItsLegitHostnameVerifier();
                }
            });
        } catch (Throwable e) {
        }
    }
    public static ClientConnectionManager getTSCCM(HttpParams params) {

        KeyStore trustStore;
        try {

            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new TrustAllSSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return ccm;

        } catch (Throwable e) {
            return null;
        }
    }

    private static ClientConnectionManager getSCCM() {

        KeyStore trustStore;
        try {

            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new TrustAllSSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new SingleClientConnManager(null, registry);

            return ccm;

        } catch (Throwable e) {
            return null;
        }
    }
    //This function determines what object we are dealing with.
    public static ClientConnectionManager getCCM(Object o, HttpParams params) {

        String className = o.getClass().getSimpleName();

        if (className.equals("SingleClientConnManager")) {
            return getSCCM();
        } else if (className.equals("ThreadSafeClientConnManager")) {
            return getTSCCM(params);
        }

        return null;
    }
    private static boolean hasTrustManagerImpl() {

        try {
            Class.forName("com.android.org.conscrypt.TrustManagerImpl");
        } catch (ClassNotFoundException e) {
            return false;
        }
        return true;
    }

    private static void JustMePlush() {
        /* Apache Hooks */
        /* external/apache-http/src/org/apache/http/impl/client/DefaultHttpClient.java */
        /* public DefaultHttpClient() */
        try {
            findAndHookConstructor(DefaultHttpClient.class, new RC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    setObjectField(param.thisObject, "defaultParams", null);
                    setObjectField(param.thisObject, "connManager", getSCCM());
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* external/apache-http/src/org/apache/http/impl/client/DefaultHttpClient.java */
        /* public DefaultHttpClient(HttpParams params) */
        try {
            findAndHookConstructor(DefaultHttpClient.class, HttpParams.class, new RC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    setObjectField(param.thisObject, "defaultParams", (HttpParams) param.args[0]);
                    setObjectField(param.thisObject, "connManager", getSCCM());
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* external/apache-http/src/org/apache/http/impl/client/DefaultHttpClient.java */
        /* public DefaultHttpClient(ClientConnectionManager conman, HttpParams params) */
        try {
            findAndHookConstructor(DefaultHttpClient.class, ClientConnectionManager.class, HttpParams.class, new RC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    HttpParams params = (HttpParams) param.args[1];

                    setObjectField(param.thisObject, "defaultParams", params);
                    setObjectField(param.thisObject, "connManager", getCCM(param.args[0], params));
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* external/apache-http/src/org/apache/http/conn/ssl/SSLSocketFactory.java */
        /* public SSLSocketFactory( ... ) */
        try {
            findAndHookConstructor(SSLSocketFactory.class, String.class, KeyStore.class, String.class, KeyStore.class,
                    SecureRandom.class, HostNameResolver.class, new RC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                            String algorithm = (String) param.args[0];
                            KeyStore keystore = (KeyStore) param.args[1];
                            String keystorePassword = (String) param.args[2];
                            SecureRandom random = (SecureRandom) param.args[4];

                            KeyManager[] keymanagers = null;
                            TrustManager[] trustmanagers = null;

                            if (keystore != null) {
                                keymanagers = (KeyManager[]) callStaticMethod(SSLSocketFactory.class, "createKeyManagers", keystore, keystorePassword);
                            }

                            trustmanagers = new TrustManager[]{new ImSureItsLegitTrustManager()};

                            setObjectField(param.thisObject, "sslcontext", SSLContext.getInstance(algorithm));
                            callMethod(getObjectField(param.thisObject, "sslcontext"), "init", keymanagers, trustmanagers, random);
                            setObjectField(param.thisObject, "socketfactory",
                                    callMethod(getObjectField(param.thisObject, "sslcontext"), "getSocketFactory"));
                        }

                    });
        } catch (Throwable e) {
            e.printStackTrace();
        }


        /* external/apache-http/src/org/apache/http/conn/ssl/SSLSocketFactory.java */
        /* public static SSLSocketFactory getSocketFactory() */
        try {
            findAndHookMethod("org.apache.http.conn.ssl.SSLSocketFactory", mLoader,
                    "getSocketFactory", new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return (SSLSocketFactory) newInstance(SSLSocketFactory.class);
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* external/apache-http/src/org/apache/http/conn/ssl/SSLSocketFactory.java */
        /* public boolean isSecure(Socket) */
        try {
            findAndHookMethod("org.apache.http.conn.ssl.SSLSocketFactory", mLoader, "isSecure", Socket.class, new RC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return true;
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* JSSE Hooks */
        /* libcore/luni/src/main/java/javax/net/ssl/TrustManagerFactory.java */
        /* public final TrustManager[] getTrustManager() */
        try {
            findAndHookMethod("javax.net.ssl.TrustManagerFactory", mLoader, "getTrustManagers", new RC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    if (hasTrustManagerImpl()) {
                        Class<?> cls = findClass("com.android.org.conscrypt.TrustManagerImpl", mLoader);

                        TrustManager[] managers = (TrustManager[]) param.getResult();
                        if (managers.length > 0 && cls.isInstance(managers[0]))
                            return;
                    }

                    param.setResult(new TrustManager[]{new ImSureItsLegitTrustManager()});
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* libcore/luni/src/main/java/javax/net/ssl/HttpsURLConnection.java */
        /* public void setDefaultHostnameVerifier(HostnameVerifier) */
        try {
            findAndHookMethod("javax.net.ssl.HttpsURLConnection", mLoader,
                    "setDefaultHostnameVerifier",
                    HostnameVerifier.class, new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });

        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* libcore/luni/src/main/java/javax/net/ssl/HttpsURLConnection.java */
        /* public void setSSLSocketFactory(SSLSocketFactory) */
        try {
            findAndHookMethod("javax.net.ssl.HttpsURLConnection", mLoader, "setSSLSocketFactory", javax.net.ssl.SSLSocketFactory.class,
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* libcore/luni/src/main/java/javax/net/ssl/HttpsURLConnection.java */
        /* public void setHostnameVerifier(HostNameVerifier) */
        try {
            findAndHookMethod("javax.net.ssl.HttpsURLConnection", mLoader, "setHostnameVerifier", HostnameVerifier.class,
                    new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
        }


        /* WebView Hooks */
        /* frameworks/base/core/java/android/webkit/WebViewClient.java */
        /* public void onReceivedSslError(Webview, SslErrorHandler, SslError) */

        try {
            findAndHookMethod("android.webkit.WebViewClient", mLoader, "onReceivedSslError",
                    WebView.class, SslErrorHandler.class, SslError.class, new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            ((SslErrorHandler) param.args[1]).proceed();
                            return null;
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /* frameworks/base/core/java/android/webkit/WebViewClient.java */
        /* public void onReceivedError(WebView, int, String, String) */

        try {
            findAndHookMethod("android.webkit.WebViewClient", mLoader, "onReceivedError",
                    WebView.class, int.class, String.class, String.class, new RC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        //SSLContext.init >> (null,ImSureItsLegitTrustManager,null)
        try {
            findAndHookMethod("javax.net.ssl.SSLContext", mLoader, "init",
                    KeyManager[].class, TrustManager[].class, SecureRandom.class, new RC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                            param.args[0] = null;
                            param.args[1] = new TrustManager[]{new ImSureItsLegitTrustManager()};
                            param.args[2] = null;

                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
        }



        /* Only for newer devices should we try to hook TrustManagerImpl */
        if (hasTrustManagerImpl()) {
            /* TrustManagerImpl Hooks */
            /* external/conscrypt/src/platform/java/org/conscrypt/TrustManagerImpl.java */

            /* public void checkServerTrusted(X509Certificate[] chain, String authType) */
            try {
                findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", mLoader,
                        "checkServerTrusted", X509Certificate[].class, String.class,
                        new RC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                return 0;
                            }
                        });
            } catch (Throwable e) {
                e.printStackTrace();
            }

            /* public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                    String authType, String host) throws CertificateException */
            try {
                findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", mLoader,
                        "checkServerTrusted", X509Certificate[].class, String.class,
                        String.class, new RC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
                                return list;
                            }
                        });
            } catch (Throwable e) {
                e.printStackTrace();
            }


            /* public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                    String authType, SSLSession session) throws CertificateException */
            try {
                findAndHookMethod("com.android.org.conscrypt.TrustManagerImpl", mLoader,
                        "checkServerTrusted", X509Certificate[].class, String.class,
                        SSLSession.class, new RC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
                                return list;
                            }
                        });
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

    } // End Hooks


}

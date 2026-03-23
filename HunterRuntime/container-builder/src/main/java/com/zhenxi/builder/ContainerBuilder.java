package com.zhenxi.builder;


import com.android.apksig.ApkSigner;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.hunter.buildsrc.RunTimeConstants;
import com.zhenxi.meditor.ManifestModificationProperty;
import com.zhenxi.meditor.NodeValue;
import com.zhenxi.meditor.utils.CLog;
import com.zhenxi.meditor.utils.Log;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jf.android.dex.Dex;
import org.jf.android.dx.command.dexer.DxContext;
import org.jf.android.dx.merge.CollisionPolicy;
import org.jf.android.dx.merge.DexMerger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import bin.mt.apksign.V2V3SchemeSigner;
import bin.mt.apksign.key.JksSignatureKey;
import bin.zip.DataMultiplexing;
import bin.zip.ZipEntry;
import bin.zip.ZipFile;
import bin.zip.ZipMaker;


public class ContainerBuilder {

    public static RuntimeBuilderContext mBuilderContext;


    public static void injectFile() {
        injectOrigApk(mBuilderContext);
    }





    public static void installBuilderApk(File outFile) {
        if (mBuilderContext.mIsInstall) {
            //执行安装命令
            String adbPath = "adb"; // 如果adb不在系统路径中，请提供完整的adb路径
            ProcessBuilder processBuilder = new ProcessBuilder(adbPath, "install", "-r", outFile.getAbsolutePath());
            CLog.e(adbPath+" "+"install"+" -r "+outFile.getAbsolutePath());
            processBuilder.redirectErrorStream(true);
            try {
                Process process = processBuilder.start();
                InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream());
                BufferedReader reader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println(line);
                }
                int exitCode = process.waitFor();
                reader.close();
                process.destroy();
                inputStreamReader.close();
            } catch (IOException | InterruptedException e) {
                System.out.println("install -r error " + e);
            }
        }else {
            CLog.e("not find -r ");
        }
    }

    public static void cleanWorkingDir() {
        CLog.i("clean working directory..");
        try {
            if (theWorkDir != null&&theWorkDir.exists()) {
                //清除本地生成的杂鱼文件,执行到这步说明已经感染完毕
                FileUtils.deleteDirectory(theWorkDir);
                System.out.println("clean working directory success ! ");
            } else {
                System.out.println("clean working directory,but file null ");
            }
        } catch (IOException e) {
            CLog.i("cleanWorkingDir error "+e);
        }
    }

    public static void signOutPutApk(CommandLine cmd, String outFile) throws Exception {
        System.out.println("the new apk file : " + outFile);
        if (cmd.hasOption('s')) {
            File sign_path = null;
            //sign path dir
            if (mBuilderContext.sign_file_path == null) {
                sign_path = new File(theWorkDir.getParent(),RunTimeConstants.zhenxiDefaultApkSignatureKey);
            }else {
                sign_path = mBuilderContext.sign_file_path;
            }
            System.out.println("signature apk file path  " +sign_path);
            if (!sign_path.exists()) {
                System.out.println("signature apk file not find !  " +
                        RunTimeConstants.zhenxiDefaultApkSignatureKey+" "+sign_path);
                System.exit(0);
            }
            //签名文件拷贝到工作目录
            InputStream signInputStream = Files.newInputStream(sign_path.toPath());
            File signatureKeyFile = new File(theWorkDir, RunTimeConstants.zhenxiDefaultApkSignatureKey);
            System.out.println("release zhenxi default apk signature key into : " + signatureKeyFile.getAbsolutePath());
            copyAndClose(signInputStream, Files.newOutputStream(signatureKeyFile.toPath()));

            //signatureV1(outFile, signatureKeyFile);

            File tmpOutputApk = File.createTempFile("optimize", ".apk");
            tmpOutputApk.deleteOnExit();
            DataMultiplexing.optimize(new File(outFile), tmpOutputApk, "assets/" + RunTimeConstants.Runtime_ORIGIN_APK_NAME, false);
            Files.move(tmpOutputApk.toPath(), new File(outFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                signatureV2V3(new File(outFile), signatureKeyFile);
                CLog.i(">>>>>>>>>>>>>> apk signatureV2V3 success !");
            } catch (Throwable e) {
                System.err.println(">>>>>>>>>>>>>>  sign error " + e.getMessage());
                signatureV2V3(new File(outFile), signatureKeyFile);
            }
        }
        else {
            System.err.println(">>>>>>>>>>>>>>  build apk not sign , not find -s ");
            // 数据复用优化
            File tmpOutputApk = File.createTempFile("optimize", ".apk");
            tmpOutputApk.deleteOnExit();
            DataMultiplexing.optimize(new File(outFile), tmpOutputApk, "assets/" + RunTimeConstants.Runtime_ORIGIN_APK_NAME, false);
            Files.move(tmpOutputApk.toPath(), new File(outFile).toPath() , StandardCopyOption.REPLACE_EXISTING);
            // 不进行签名,保留原始apk签名
        }
    }

    private static void injectOrigApk(RuntimeBuilderContext zhenxiBuilderContext) {
        //写入原始apk文件
        zhenxiBuilderContext.outPutZipMaker.setMethod(ZipMaker.METHOD_STORED);
        try {
            long size = new File(zhenxiBuilderContext.outApkFilePath).getTotalSpace();
            Util.copyAssets(
                    zhenxiBuilderContext.outPutZipMaker,
                    new File(zhenxiBuilderContext.inApkFilePath),
                    RunTimeConstants.Runtime_ORIGIN_APK_NAME
            );
            long new_size = new File(zhenxiBuilderContext.outApkFilePath).getTotalSpace();
            CLog.i("inject orig apk success ! " + size + "->" + new_size);
        } catch (Throwable e) {
            CLog.e("ContainerBuilder->injectOrigApk error " + e);
            System.exit(0);
        }
        zhenxiBuilderContext.outPutZipMaker.setMethod(ZipMaker.METHOD_DEFLATED);
    }


    /**
     * 1、把除了dex以外的全部文件都进行拷贝,拷贝文件到目标apk,把输入文件[input]拷贝到输出文件[output]
     * 2、计算append index
     */
    public static List<ZipEntry> copyInPutApkFile(RuntimeBuilderContext zhenxiBuilderContext) {
        //保存原始apk全部的dex文件
        List<ZipEntry> dexEntries = Lists.newArrayList();
        //把原始apk的内容拷贝到新的apk里面
        try {
            for (ZipEntry zipEntry : zhenxiBuilderContext.inputZipFile.getEntries()) {
                String entryName = zipEntry.getName();
                //dex不进行拷贝
                if (entryName.startsWith("classes") &&entryName.endsWith(".dex") && !entryName.contains("/")) {
                    dexEntries.add(zipEntry);
                    continue;
                }
                //xml不进行拷贝
                if (entryName.equals(RunTimeConstants.manifestFileName)) {
                    continue;
                }
                copyEntry(zipEntry, zhenxiBuilderContext.inputZipFile, zhenxiBuilderContext.outPutZipMaker);
            }
        } catch (Throwable e) {
            CLog.e("ContainerBuilder->copyInPutApkFile error " + e);
        }
        if (dexEntries.isEmpty()) {
            //input apk本身一个dex不存在,默认从1开始
            zhenxiBuilderContext.dexAppendIndex = 1;
        } else {
            //case:
            //原来apk有15个dex,dexAppendIndex 应该是16
            zhenxiBuilderContext.dexAppendIndex = dexEntries.size()+1;
        }
        CLog.i(zhenxiBuilderContext.inApkFilePath
                + " dex size -> " + dexEntries.size());
        CLog.i( "out temp file size  " + new File(zhenxiBuilderContext.outApkFilePath).length());
        return dexEntries;
    }

    public static void initBuilder(CommandLine cmd, Options options) throws IOException {
        try {
            mBuilderContext = RuntimeBuilderContext.from(cmd.getArgList());
            if (cmd.hasOption("target")) {
                mBuilderContext.rebuild_targetSdkVersion = cmd.getOptionValue("target");
            }
            if (cmd.hasOption("r")) {
                mBuilderContext.mIsInstall = true;
            }
            if (cmd.hasOption('m')) {
                mBuilderContext.mappingPath = cmd.getOptionValue('m');
            }
            if (cmd.hasOption("hack_camera")) {
                mBuilderContext.hackCameraPath = cmd.getOptionValue("hack_camera");
            }
            System.out.println("hackCameraPath -> "+mBuilderContext.hackCameraPath);
            System.out.println("mappingPath -> "+mBuilderContext.mappingPath);

            //工作目录准备
            File outFile;
            if (cmd.hasOption('o')) {
                outFile = new File(cmd.getOptionValue('o'));
            } else {
                outFile = new File(mBuilderContext.apkMeta.getPackageName()
                        + "_" + mBuilderContext.apkMeta.getVersionName()
                        + "_" + mBuilderContext.apkMeta.getVersionCode() + "_patched.apk");
            }
            if(outFile.exists()){
                outFile.delete();
            }
            mBuilderContext.outPutZipMaker = new ZipMaker(outFile);
            mBuilderContext.outApkFilePath = outFile.getPath();

            mBuilderContext.cmd = cmd;
            mBuilderContext.genMeta();
        } catch (Throwable e) {
            CLog.e("ContainerBuilder->initBuilder error " + e,e);
        }
    }

    /**
     * 感染阶段计算Apk签名信息,保存到Properties文件里面 。
     */
    public static void initApkSign() {
        //计算apk签名,将apk签名打入到被重打包的apk内部,防止每次在启动apk计算签名浪费时间。
        //在重打包时候将apk计算好,使用字符串保存
        String apkSignInfo = ApkSignatureHelper.getApkSignInfo(mBuilderContext.inApkFilePath);
        if (apkSignInfo == null) {
            System.err.println("build get apk sign error : " + mBuilderContext.inApkFilePath);
            System.exit(0);
        }
        mBuilderContext.ZhenxiBuildProperties.setProperty(RunTimeConstants.ORIG_APK_SIGN, apkSignInfo);
    }

    public static void copyEngineApkToWorkDir() throws IOException {
        File origEngineApk = new File(new File(workDir().getParent(), "temp"), RunTimeConstants.RUNTIME_ENGINE_RESOURCE_APK_NAME);
        if (!origEngineApk.exists()) {
            System.out.println("!!!!!!!!!!!!! not find "
                    + RunTimeConstants.RUNTIME_ENGINE_RESOURCE_APK_NAME +
                    "  " + origEngineApk.getPath());
            System.exit(0);
        }
        File zhenxiEngineApk = new File(theWorkDir, RunTimeConstants.RUNTIME_ENGINE_RESOURCE_APK_NAME);
        System.out.println("release zhenxi engine apk into: " + zhenxiEngineApk.getAbsolutePath());
        copyAndClose(
                Files.newInputStream(origEngineApk.toPath()),
                Files.newOutputStream(zhenxiEngineApk.toPath())
        );

    }

//    ZipMaker生成的文件已进行了ZipAlign，不再需要该方法
//    private static void zipalign(File outApk, File theWorkDir) throws IOException, InterruptedException {
//        System.out.println("zip align output apk: " + outApk);
//        //use different executed binary file with certain OS platforms
//        String osName = System.getProperty("os.name").toLowerCase();
//        String zipalignBinPath;
//        boolean isLinux = false;
//        if (osName.startsWith("Mac OS".toLowerCase())) {
//            zipalignBinPath = "zipalign/mac/zipalign";
//        } else if (osName.startsWith("Windows".toLowerCase())) {
//            zipalignBinPath = "zipalign/windows/zipalign.exe";
//        } else {
//            zipalignBinPath = "zipalign/linux/zipalign";
//            isLinux = true;
//        }
//        File unzipDestFile = new File(theWorkDir, zipalignBinPath);
//        unzipDestFile.getParentFile().mkdirs();
//        copyAndClose(ContainerBuilder.class.getClassLoader().getResourceAsStream(zipalignBinPath), new FileOutputStream(unzipDestFile));
//        if (isLinux) {
//            String libCPlusPlusPath = "zipalign/linux/lib64/libc++.so";
//            File libCPlusPlusFile = new File(theWorkDir, libCPlusPlusPath);
//            libCPlusPlusFile.getParentFile().mkdirs();
//            copyAndClose(ContainerBuilder.class.getClassLoader().getResourceAsStream(libCPlusPlusPath), new FileOutputStream(libCPlusPlusFile));
//        }
//
//        unzipDestFile.setExecutable(true);
//
//        File tmpOutputApk = File.createTempFile("zipalign", ".apk");
//        tmpOutputApk.deleteOnExit();
//
//        String command = unzipDestFile.getAbsolutePath() + " -f  4 " + outApk.getAbsolutePath() + " " + tmpOutputApk.getAbsolutePath();
//        System.out.println("zip align apk with command: " + command);
//
//        String[] envp = new String[]{"LANG=zh_CN.UTF-8", "LANGUAGE=zh_CN.UTF-8"};
//        Process process = Runtime.getRuntime().exec(command, envp, null);
//        autoFillBuildLog(process.getInputStream(), "zipalign-stand");
//        autoFillBuildLog(process.getErrorStream(), "zipalign-error");
//
//        process.waitFor();
//
//        Files.move(
//                tmpOutputApk.toPath(), outApk.toPath(), StandardCopyOption.REPLACE_EXISTING);
//        System.out.println(outApk.getAbsolutePath() + " has been zipalign ");
//    }

    private static void autoFillBuildLog(InputStream inputStream, String type) {
        new Thread("read-" + type) {
            @Override
            public void run() {
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        System.out.println(type + " : " + line);
                    }
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * 传入一个apk路径,生产一个临时文件,作为输出
     * 执行完毕把这个临时文件移动到输入的apk路径。
     */
    private static void signatureV1(File inApk, File keyStoreFile) {
        try {
            File tempOut = File.createTempFile("SignV1", ".apk");
            tempOut.deleteOnExit();
            // Load the keystore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream keyStoreStream = new FileInputStream(keyStoreFile)) {
                keyStore.load(keyStoreStream, "hermes".toCharArray());
            }
            // Get the private key and certificate from the keystore
            PrivateKey privateKey = (PrivateKey)
                    keyStore.getKey("hermes", "hermes".toCharArray());
            X509Certificate certificate =
                    (X509Certificate) keyStore.getCertificate("hermes");
            // Create a list of certificates
            List<X509Certificate> certificates = new ArrayList<>();
            certificates.add(certificate);
            // Create an ApkSigner instance
            ApkSigner.SignerConfig signerConfig
                    = new ApkSigner.SignerConfig.
                    Builder("hermes", privateKey, certificates).build();
            // Create a list of SignerConfig
            List<ApkSigner.SignerConfig> signerConfigs = new ArrayList<>();
            signerConfigs.add(signerConfig);
            // Create an ApkSigner.Builder with the list of SignerConfig
            ApkSigner.Builder apkSignerBuilder = new ApkSigner.Builder(signerConfigs);
            apkSignerBuilder.
                    setInputApk(inApk).
                    setOutputApk(tempOut).
                    setOtherSignersSignaturesPreserved(false).
                    setV1SigningEnabled(true).
                    setV2SigningEnabled(false).
                    setV3SigningEnabled(false).
                    setV4SigningEnabled(false).
                    //允许debug包签名
                            setDebuggableApkPermitted(true);
            ApkSigner apkSigner = apkSignerBuilder.build();
            apkSigner.sign();
            Files.move(tempOut.toPath(), inApk.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (inApk.exists()) {
                CLog.i(">>>>>>>>> apk sign v1 finish success ! ");
            } else {
                CLog.e(">>>>>>>>> apk sign v1 finish ,file not find  ! ");
            }
        } catch (Throwable e) {
            CLog.e(">>>>>>>>> apk sign v1 error " + e, e);
        }
    }

    private static void signatureV2V3(File outApk, File keyStoreFile) throws Exception {
        System.out.println("auto sign apk with zhenxi KeyStore");
        V2V3SchemeSigner.sign(outApk,
                new JksSignatureKey(keyStoreFile,
                        "hermes",
                        "hermes",
                        "hermes"),
                true,
                true);
        System.out.println(outApk.getAbsolutePath() + " has been Signed");
    }

    static String setDexName(int dex){
        if (dex == 1) {
            return "classes.dex";
        } else {
            return "classes" + dex + ".dex";
        }
    }
    public static void injectzhenxiResource(RuntimeBuilderContext zhenxiBuilderContext) throws IOException {
        ZipFile zhenxiEngineApkFile = new ZipFile(new File(theWorkDir, RunTimeConstants.RUNTIME_ENGINE_RESOURCE_APK_NAME));
        ArrayList<ZipEntry> entries = zhenxiEngineApkFile.getEntries();
        CLog.i("append dex index -> "+zhenxiBuilderContext.dexAppendIndex );
        // copy dex
        for (ZipEntry zipEntry : entries) {
            String entryName = zipEntry.getName();
            if (entryName.startsWith("classes") && (Util.classesIndexPattern.matcher(entryName).matches() || entryName.equals("classes.dex"))) {
                zipEntry.setName(setDexName(zhenxiBuilderContext.dexAppendIndex));
                zhenxiBuilderContext.outPutZipMaker.copyZipEntry(zipEntry, zhenxiEngineApkFile);
                CLog.i(">>>>>>>>>>>> write in dex name  " + zipEntry.getName());
                zhenxiBuilderContext.dexAppendIndex++;
            } else if (entryName.startsWith("lib/")) {
                // 不需要注入frida
                if (!zhenxiBuilderContext.cmd.hasOption("f") && entryName.contains("frida-gadget.so")) {
                    continue;
                }
                List<String> pathSegment = Splitter.on("/").splitToList(entryName);
                String arch = pathSegment.get(1);
                if (zhenxiBuilderContext.orignApkSupportArch.contains(arch)
                        || zhenxiBuilderContext.orignApkSupportArch.isEmpty()) {
                    zhenxiBuilderContext.outPutZipMaker.copyZipEntry(zipEntry, zhenxiEngineApkFile);
                } else if (zhenxiBuilderContext.orignApkSupportArch.contains("armeabi") && arch.equals("armeabi-v7a")) {
                    // 如果没有armeabi-v7a，那么转化到armeabi上面
                    zipEntry.setName("lib/armeabi/" + pathSegment.get(2));
                    zhenxiBuilderContext.outPutZipMaker.copyZipEntry(zipEntry, zhenxiEngineApkFile);
                } else {
                    System.out.println("un support lib abi:"
                            + zhenxiBuilderContext.orignApkSupportArch + " " + arch);
                }

            }
        }
        zhenxiEngineApkFile.close();


    }

    public static void editManifest(RuntimeBuilderContext zhenxiBuilderContext) {
        try {
            ZipEntry xmlZipEntry = zhenxiBuilderContext.inputZipFile.getEntry(RunTimeConstants.manifestFileName);
            InputStream inputStream = zhenxiBuilderContext.inputZipFile.getInputStream(xmlZipEntry); // 不需要关闭
            byte[] orgXmlbytes = IOUtils.toByteArray(inputStream);
            byte[] newXmlbytes = editManifestWithAXmlEditor(orgXmlbytes, zhenxiBuilderContext);
            //字节数组不相等的时候说明,xml有修改。进行覆盖写入。
            System.out.println("need rebuild xml ");
            zhenxiBuilderContext.outPutZipMaker.putNextEntry(RunTimeConstants.manifestFileName);
            zhenxiBuilderContext.outPutZipMaker.write(newXmlbytes);
        } catch (IOException e) {
            System.out.println("editManifest error " + e.getMessage());
        }
    }

    /**
     * 修改xml的方法
     */
    private static byte[] editManifestWithAXmlEditor(byte[] manifestFileData, RuntimeBuilderContext zhenxiBuilderContext) throws IOException {

        System.out.println(">>>>>>>>>>> start  editManifestWithAXmlEditor ");
        ManifestModificationProperty properties = new ManifestModificationProperty();

        boolean modify = false;

        if (zhenxiBuilderContext.cmd.hasOption('d')) {
            System.out.println(">>>>>>>>>>> replace debug moudle  ");
            modify = true;
            properties.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, true));
        }
        if (zhenxiBuilderContext.cmd.hasOption("rw_sdcard")) {
            System.out.println(">>>>>>>>>>> add read & write sdcard ");
            properties.addUsesPermission( "android.permission.WRITE_EXTERNAL_STORAGE");
            properties.addUsesPermission( "android.permission.READ_EXTERNAL_STORAGE");
            modify = true;
        }
        if (!zhenxiBuilderContext.cmd.hasOption("dex")) {
            //非dex插入
            modify = true;
            Log.e(">>>>>>>>>>>  replace origin application [" +
                    zhenxiBuilderContext.originApplicationClass + "] -> "
                    + (StringUtils.isBlank(zhenxiBuilderContext.originApplicationClass) ?
                    RunTimeConstants.SimpleRuntimeApplicationClassName : RunTimeConstants.RuntimeApplicationClassName));
            //这块可能存在两种情况
            //1,原始apk存在application,但是xml替换name
            //2,原始apk不存在application,这时候就不需要重写onCreate逻辑直接静态代码快插入,
            //也不需要对application进行修改和替换
            if (StringUtils.isBlank(zhenxiBuilderContext.originApplicationClass)) {
                properties.addApplicationAttribute(
                        new AttributeItem(NodeValue.Application.NAME, RunTimeConstants.SimpleRuntimeApplicationClassName));
            } else {
                properties.addApplicationAttribute(
                        new AttributeItem(NodeValue.Application.NAME, RunTimeConstants.RuntimeApplicationClassName));
            }
        }

        if(zhenxiBuilderContext.dexInsetNoApplication!=null){
            modify = true;
            //dex插入,但是无application
            properties.addApplicationAttribute(
                    new AttributeItem(NodeValue.Application.NAME, RunTimeConstants.SimpleRuntimeApplicationClassName));
        }

        if (zhenxiBuilderContext.cmd.hasOption("factory")) {
            //爱加密会将这个字段设置成 shell A
            //导致静态代码块初始化失败,替换成空实现
            System.out.println(">>>>>>>>>>> replace appComponentFactory  ");
            modify = true;
            properties.addApplicationAttribute(new AttributeItem("appComponentFactory", RunTimeConstants.RuntimeComponentFactoryClassName));
        }

        // 重打包安以后出现下面错误请在重打包时候加上extract配置参数
        // Failure [INSTALL_FAILED_INVALID_APK:
        // Failed to extract native libraries, res=-2]
        if (zhenxiBuilderContext.cmd.hasOption("extract")) {
            System.out.println(">>>>>>>>>>> replace extractNativeLibs  ");
            modify = true;
            properties.addApplicationAttribute(new AttributeItem("extractNativeLibs", "true"));
        }

        if (zhenxiBuilderContext.cmd.hasOption("removezygote")) {
            System.out.println(">>>>>>>>>>> replace zygotePreloadName  ");
            //这个字段可以在xml里面配置,可以在App zygote进程进行初始化
            //在我们hook之前进行初始化,导致我们的Hook没生效。直接替换成我们的空实现
            modify = true;
            properties.addApplicationAttribute(new AttributeItem("zygotePreloadName", RunTimeConstants.RuntimeZygotePreloadClassName));
        }
        if (zhenxiBuilderContext.cmd.hasOption("removeHasCode")) {
            System.out.println(">>>>>>>>>>> replace android:hasCode=false  ");
            //android:hasCode=false 添加以后apk里面不允许添加任何dex文件
            //导致找不到我们的application提示class not find
            modify = true;
            properties.addApplicationAttribute(new AttributeItem("hasCode", "true"));
        }
        if (zhenxiBuilderContext.cmd.hasOption("target")) {
            if(zhenxiBuilderContext.rebuild_targetSdkVersion==null){
                System.err.println(">>>>>>>>>>> rebuild android:targetSdkVersion not find tag  ");
            }else {
                System.out.println(">>>>>>>>>>> replace android:targetSdkVersion = " +zhenxiBuilderContext.rebuild_targetSdkVersion);
                //android:hasCode=false 添加以后apk里面不允许添加任何dex文件
                //导致找不到我们的application提示class not find
                modify = true;
//                properties.addUsesPermission(
//                        "targetSdkVersion"
//                );
            }
        }
        if (modify) {
            return ManifestHandlers.editManifestXML(manifestFileData, properties);
        } else {
            return manifestFileData;
        }
    }

    /**
     * 因为上个方法没有拷贝dex和xml
     * 1、如果是dex插入模式 。
     * 执行dex合并,将生产的dexlist插入到apk里面 。
     * 2、如果是正常的xml重构直接写入
     */
    public static void migrateBasicFile(CommandLine cmd,
                                         RuntimeBuilderContext zhenxiBuilderContext,
                                         List<ZipEntry> dexEntries) throws IOException {
        if (!zhenxiBuilderContext.cmd.hasOption("dex")) {
            // 不是reBuildDex模式，不需要处理,把全部的文件直接拷贝过来
            for (ZipEntry zipEntry : dexEntries) {
                copyEntry(zipEntry, zhenxiBuilderContext.inputZipFile, zhenxiBuilderContext.outPutZipMaker);
            }
        } else {
            if (StringUtils.isBlank(zhenxiBuilderContext.originApplicationClass)) {
                //静态代码快插入,但是不存在application的情况,可能是apk没有dex
                Log.e("build apk not find application !!!!");
                Log.e("build apk not find application !!!!");
                Log.e("build apk not find application !!!!");
                //伪造我们自己的application
                zhenxiBuilderContext.dexInsetNoApplication = "com.setting.runtime.SimpleRuntimeApplication";
                //直接拷贝即可
                for (ZipEntry zipEntry : dexEntries) {
                    copyEntry(zipEntry, zhenxiBuilderContext.inputZipFile, zhenxiBuilderContext.outPutZipMaker);
                }
                return;
            }
            int orig_dexsize = dexEntries.size();
            Map<String, DexZipEntryPair> rebuildDexMap
                    = rebuildDex(zhenxiBuilderContext, dexEntries);
            if (rebuildDexMap == null) {
                Log.e("build apk rebuild dex fail ! ");
                System.exit(0);
                return;
            }
            CLog.i("build apk rebuild dex size  "
                    + orig_dexsize+"->"+rebuildDexMap.size()
            );
            int index = 1;
            for (String key : rebuildDexMap.keySet()) {
                DexZipEntryPair dexZipEntry = rebuildDexMap.get(key);
                dexZipEntry.originZipEntry.setName(setDexName(index));
                System.out.println("write orig rebuild dex  -> " + key + " " + dexZipEntry.originZipEntry.getName());

                zhenxiBuilderContext.outPutZipMaker.putNextEntry(dexZipEntry.originZipEntry);
                try (FileInputStream stream = new FileInputStream(dexZipEntry.realContent)) {
                    zhenxiBuilderContext.outPutZipMaker.writeFully(stream);
                }
                index++;
            }
            //新的基础上+1
            zhenxiBuilderContext.dexAppendIndex = rebuildDexMap.size()+1;
            CLog.i("up data dexAppendIndex "+zhenxiBuilderContext.dexAppendIndex);
        }

    }

    public static class DexZipEntryPair {

        ZipEntry originZipEntry;
        File realContent;

        public DexZipEntryPair(ZipEntry originZipEntry, File realContent) {
            this.originZipEntry = originZipEntry;
            this.realContent = realContent;
        }

        @Override
        public String toString() {
            return "DexZipEntryPair{" +
                    "originZipEntry=" + originZipEntry.getName() +
                    ", realContent=" + realContent +
                    '}';
        }

    }

    /**
     * dex重编的方式织入注入代码
     *
     * @param zhenxiBuilderContext build上下文
     * @param zipEntries           APK所有Dex文件
     * @throws IOException
     */
    private static Map<String, DexZipEntryPair> rebuildDex(RuntimeBuilderContext zhenxiBuilderContext,
                                                           List<ZipEntry> zipEntries
    ) throws IOException {
        System.out.println("org application name -> " +
                zhenxiBuilderContext.originApplicationClass);
        Map<String, DexZipEntryPair> dexZipEntryPairs = new HashMap<>();
        //记录最大的dex index
        int maxIndex = 1;

        //是否需要dex拆分,解决65535问题
        File splitDexFile = null;
        boolean hasSplitDex = false;


        //这个for主要两个功能
        File originApplicationSmali = null;
        //找到包含application的samil并解压。
        for (int i = 0; i < zipEntries.size(); i++) {
            ZipEntry zipEntry = zipEntries.get(i);
            //dex文件名称,classes.dex 类似
            String entryName = zipEntry.getName();
            //增加temp 前缀
            String tempEntryName = "getSmail_temp_" + entryName;
            String workDir = workDir().getAbsolutePath();
            File tempDexFile = new File(workDir, tempEntryName);
            FileUtils.writeByteArrayToFile(tempDexFile, IOUtils.toByteArray(zhenxiBuilderContext.inputZipFile.getInputStream(zipEntry)));
            //判断dex里面是否包含originApplicationClass
            if (ApplicationInsertHandler.hasClass(tempDexFile, zhenxiBuilderContext.originApplicationClass)) {
                //对包含application 的dex做处理，从dex 解析xml 对应的 smali 文件
                originApplicationSmali = ApplicationInsertHandler.disassembleSmaliFromDex(
                        tempDexFile,
                        zhenxiBuilderContext.originApplicationClass,
                        workDir
                );
            }

            if (originApplicationSmali != null) {
                System.out.println("find orig Application smali success ！ " + originApplicationSmali.getPath());
                tempDexFile.delete();
                break;
            }
            //删掉保存的文件不影响之后逻辑 。
            tempDexFile.delete();
        }
        //没有找到原始dex的smali文件
        if (originApplicationSmali == null) {
            System.out.println("not find orig Application smali file ! " + zhenxiBuilderContext.originApplicationClass);
            return null;
        }
        String findTopApplication = "Landroidx/multidex/MultiDexApplication;";
        ApplicationInsertHandler.ApplicationPair applicationPair = null;
        //这个for是为了找到top的application,需要先把dex释放。
        for (int i = 0; i < zipEntries.size(); i++) {
            ZipEntry zipEntry = zipEntries.get(i);
            //dex文件名称,classes.dex 类似
            String entryName = zipEntry.getName();
            //增加temp 前缀
            String tempEntryName = "temp_" + entryName;
            String workDir = workDir().getAbsolutePath();
            File tempDexFile = new File(workDir, tempEntryName);
            FileUtils.writeByteArrayToFile(tempDexFile, IOUtils.toByteArray(zhenxiBuilderContext.inputZipFile.getInputStream(zipEntry)));
            //先查找MultiDexApplication,如果找不到在查找Application 。
            applicationPair = ApplicationInsertHandler.findParentApplication(
                    originApplicationSmali,
                    tempDexFile,
                    workDir(),
                    "temp_",findTopApplication);
            if(applicationPair == null) {
                findTopApplication = "Landroid/app/Application;";
                CLog.e("start find [Landroid/app/Application;] top ");
                //递归查找最上级application
                applicationPair = ApplicationInsertHandler.findParentApplication(
                        originApplicationSmali,
                        tempDexFile,
                        workDir(),
                        "temp_", findTopApplication
                );
            }
        }

        if (applicationPair == null || applicationPair.smaliFile == null || applicationPair.dexFile == null) {
            System.out.println("not find top application name file ! " + originApplicationSmali.getPath());
            return null;
        }

        Log.i(">>>>>>>>>>> xml application info -> [" + originApplicationSmali + "]");
        Log.i(">>>>>>>>>>> find top application success ! " + " [" + applicationPair.smaliFile + "]");


        String path = applicationPair.smaliFile.getPath();
        String[] split = path.split(workDir().getName());
        if (split.length != 2) {
            Log.e(">>>>>>>>>>> get top class info error  ->" + path + " " + Arrays.toString(split));
            System.exit(0);
        }
        //把第一个\截取掉
        split[1] = split[1].substring(1);
        path = split[1].
                replaceAll(".smali", "").
                replaceAll("\\\\", ".");


        zhenxiBuilderContext.topApplicationClass = path;
        Log.e(">>>>>>>>>>> Top Class Name  " + " [" + zhenxiBuilderContext.topApplicationClass + "]");
        //源文件中dex列表
        for (int i = 0; i < zipEntries.size(); i++) {
            ZipEntry zipEntry = zipEntries.get(i);
            //dex文件名称,classes.dex 类似
            String entryName = zipEntry.getName();
            //计算最大的dex的索引
            Matcher matcher = Util.classesIndexPattern.matcher(entryName);
            if (matcher.matches()) {
                int nowIndex = NumberUtils.toInt(matcher.group(1));
                if (nowIndex > maxIndex) {
                    maxIndex = nowIndex;
                }
            }
            //增加temp 前缀
            String tempEntryName = "temp_" + entryName;
            // has been processed
            //去重处理
            if (dexZipEntryPairs.containsKey(tempEntryName)) {
                continue;
            }

            // create temp dex file
            String workDir = workDir().getAbsolutePath();
            File tempDexFile = new File(workDir, tempEntryName);
            if (!tempDexFile.exists()) {
                FileUtils.writeByteArrayToFile(tempDexFile, IOUtils.toByteArray(zhenxiBuilderContext.inputZipFile.getInputStream(zipEntry)));
            }
            // 找到不包含top的dex,添加到zip里面,应该以topApplicationClass为标准,而不是xml的application
            if (!ApplicationInsertHandler.hasClass(tempDexFile, zhenxiBuilderContext.topApplicationClass)) {
                dexZipEntryPairs.put(tempEntryName, new DexZipEntryPair(zipEntry, tempDexFile));
                continue;
            }

            // find the clinit method
            String targetSmaliName = "L" + applicationPair.smaliFile.
                    getAbsolutePath().replace(workDir.endsWith("/") ? workDir : workDir + "/", "")
                    .replace("/", ".") + ";";

            boolean hasClinitMethod = ApplicationInsertHandler.hasClinitMethod(applicationPair.dexFile, targetSmaliName);
            System.out.println("find the final smali and dex to rebuild: " + targetSmaliName + "|" + applicationPair.dexFile.getAbsolutePath() + "|" + hasClinitMethod);

            //插入静态代码块
            File reBuiltSmali = ApplicationInsertHandler.reBuildSmali(applicationPair.smaliFile,
                    !hasClinitMethod, workDir, findTopApplication);
            if (reBuiltSmali == null || !reBuiltSmali.exists()) {
                System.out.println("!!! rebuild smali error !!!");
                System.exit(0);
            }

            for (int u = 0; u < 4; ++u) {
                //将重建的smali文件转换成dex
                String updatedDex = new File(workDir, "updated_" + entryName).getAbsolutePath();
                try {
                    org.jf.smali.Main.main(new String[]{"a", reBuiltSmali.getAbsolutePath(), "-o", updatedDex});
                } catch (Exception exception) {
                    System.out.println(">>>>>>>>>>> new smali file to dex error ! " + exception);
                    System.exit(0);
                }
                //判断重建的dex是否存在
                File file = new File(updatedDex);
                if (!file.exists()) {
                    System.out.println(">>>>>>>>>>> new smali file to dex error file not exists !");
                    System.exit(0);
                }
                try {
                    //尝试合并生成的dex,如果合并存在65535问题则会抛异常。
                    Dex[] DexList = {new Dex(file), new Dex(applicationPair.dexFile)};
                    DxContext dxContext = new DxContext();
                    DexMerger dexMerger = new DexMerger(DexList, CollisionPolicy.KEEP_FIRST, dxContext);
                    Objects.requireNonNull(dexMerger.merge()).writeTo(applicationPair.dexFile);
                    break;
                } catch (Throwable e) {
                    //System.out.println("dex merger error,maybe need split dex "+e);
                    if (hasSplitDex) {
                        System.out.println("already split dex");
                        throw e;
                    }
                    System.out.println("split dex");
                    splitDexFile = DexSplitter.splitDex(applicationPair.dexFile, workDir(), Sets.newHashSet(targetSmaliName), zhenxiBuilderContext);
                    hasSplitDex = true;
                }
            }
            // if target dex file is not current dex, keep origin dex
            boolean isSameDex = StringUtils.equals(applicationPair.dexFile.getAbsolutePath(), tempDexFile.getAbsolutePath());

            if (!isSameDex) {
                dexZipEntryPairs.put(tempEntryName, new DexZipEntryPair(zipEntry, tempDexFile));
            } else {
                dexZipEntryPairs.put(applicationPair.dexFile.getName(), new DexZipEntryPair(zipEntry, applicationPair.dexFile));
                continue;
            }

            // 如果最终smali与当前dex不是同一个dex
            DexZipEntryPair dexZipEntryPair = dexZipEntryPairs.get(applicationPair.dexFile.getName());
            if (dexZipEntryPair != null) {
                ZipEntry originEntry = dexZipEntryPair.originZipEntry;
                dexZipEntryPairs.put(applicationPair.dexFile.getName(), new DexZipEntryPair(originEntry, applicationPair.dexFile));
            } else {
                dexZipEntryPairs.put(applicationPair.dexFile.getName(), new DexZipEntryPair(new ZipEntry(entryName), applicationPair.dexFile));
            }
        }
        maxIndex++;
        //当出现拆dex的时候
        if (splitDexFile != null) {
            String entryName = "temp_classes" + maxIndex + ".dex";
            String workDir = workDir().getAbsolutePath();
            File tempDexFile = new File(workDir, entryName);
            FileInputStream fileInputStream = new FileInputStream(splitDexFile);
            FileUtils.writeByteArrayToFile(tempDexFile, IOUtils.toByteArray(fileInputStream));
            //记得关掉,否则导致异常
            fileInputStream.close();

            dexZipEntryPairs.put(entryName, new DexZipEntryPair(new ZipEntry("classes" + maxIndex + ".dex"), tempDexFile));
        }
        return dexZipEntryPairs;
    }


    /**
     * @param zipEntry 需要拷贝的zipEntry
     * @param zipFile  zipEntry所属的zipFile
     * @param zipMaker 拷贝到目标zipMaker
     * @throws IOException
     */
    private static void copyEntry(ZipEntry zipEntry, ZipFile zipFile, ZipMaker zipMaker) throws IOException {
        try {
            zipMaker.copyZipEntry(zipEntry, zipFile);
        }catch (Throwable throwable){
            CLog.e(">>>>>>>> copyEntry error "+throwable +"  "+zipEntry.toString());
        }
    }


    public static File theWorkDir = null;

    private static File workDir() {
        if (theWorkDir == null) {
            theWorkDir = new File("runtime_work_dir");
        }
        return theWorkDir;
    }

    public static void cleanWorkDir() {
        //2、clean work file dir
        File workDir = workDir();
        FileUtils.deleteQuietly(workDir);
        try {
            FileUtils.forceMkdir(workDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyAndClose(final InputStream input, final OutputStream output) throws IOException {
        IOUtils.copy(input, output);
        input.close();
        output.close();
    }


//    public static void restoreConstants() throws Exception {
//        InputStream inputStream =
//                ContainerBuilder.class.getClassLoader().
//                        getResourceAsStream(RunTimeConstants.RUNTIME_CONFIG_PROPERTIES);
//        if (inputStream == null) {
//            System.out.println(">>>>>>>>>>> container-builder restoreConstants " +
//                    "" + RunTimeConstants.RUNTIME_CONFIG_PROPERTIES + " == null ");
//            System.out.println(">>>>>>>>>>> container-builder restoreConstants " +
//                    "" + RunTimeConstants.RUNTIME_CONFIG_PROPERTIES + " == null ");
//            System.out.println(">>>>>>>>>>> container-builder restoreConstants " +
//                    "" + RunTimeConstants.RUNTIME_CONFIG_PROPERTIES + " == null ");
//            System.exit(0);
//            return;
//        }
//        Properties properties = new Properties();
//        properties.load(inputStream);
//        inputStream.close();
//
//        for (Field field : RunTimeConstants.class.getDeclaredFields()) {
//            if (field.isSynthetic()) {
//                continue;
//            }
//            if (!Modifier.isStatic(field.getModifiers())) {
//                continue;
//            }
//            if (Modifier.isFinal(field.getModifiers())) {
//                continue;
//            }
//
//            String value = properties.getProperty(RunTimeConstants.RUNTIME_CONSTANTS_PREFIX + field.getName());
//            if (value == null) {
//                continue;
//            }
//
//            Object castValue = Util.primitiveCast(value, field.getType());
//
//            if (castValue == null) {
//                continue;
//            }
//
//            if (!field.isAccessible()) {
//                field.setAccessible(true);
//            }
//            field.set(null, castValue);
//        }
//    }
}

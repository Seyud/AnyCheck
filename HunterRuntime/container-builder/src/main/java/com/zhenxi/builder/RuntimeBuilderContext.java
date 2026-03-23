package com.zhenxi.builder;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.zhenxi.meditor.utils.CLog;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.DexClass;
import net.dongliu.apk.parser.parser.DexParser;
import net.dongliu.apk.parser.struct.AndroidConstants;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import com.hunter.buildsrc.RunTimeConstants;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import bin.zip.ZipEntry;
import bin.zip.ZipFile;
import bin.zip.ZipMaker;

public class RuntimeBuilderContext {
    /**
     * 保存全部需要打入到apk包里面的变量信息
     */
    public Properties ZhenxiBuildProperties = new Properties();

    public boolean mIsInstall = false;
    /**
     * dex插入,但是没有application的情况
     */
    public String dexInsetNoApplication = null;

    public String packageName;
    public String androidAppComponentFactory;
    public String appEntryClass;
    public String appEntryClassDex;
    public Map<String, DexClass[]> dexClassesMap;
    public List<String> axmlEditorCommand;
    public String launcherActivityClass;

    /**
     * 如果修改android:targetSdkVersion
     * 此值不为null,主要目的为了修改一些apk利用低版本的tag权限去获取设备信息。
     */
    String rebuild_targetSdkVersion = null;
    /**
     * 输出的apk file
     */
    String outApkFilePath;
    /**
     * 传入的apk file
     */
    String inApkFilePath;

    ApkMeta apkMeta;

    /**
     * 输出的apk file
     */
    ZipMaker outPutZipMaker;
    /**
     * 输入的apk file
     */
    ZipFile inputZipFile;
    /**
     * 签名文件路径,如果不设置默认使用工作目录的父目录里面的签名文件 。
     */
    public File sign_file_path = null;
    /**
     * 自动化测试hook人脸的zip包
     */
    String hackCameraPath ;
    /**
     * 混淆的文件,用于hook混淆以后的Apk
     */
    String mappingPath ;
    /**
     * 最小值为1,如果dexAppendIndex == 1
     * 写入的dex应该是classes,以此类推如下 。
     * classes2
     * classes3
     * ...
     */
    int dexAppendIndex = -1;

    public CommandLine cmd;

    private String newPkgName = null;
    String originApplicationClass;
    String topApplicationClass ;
    public Document originApplicationManifestDoc;

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

    Set<String> orignApkSupportArch;


    public static RuntimeBuilderContext from(List<String> argList) throws Exception {
        if (argList.isEmpty()) {
            throw new IllegalStateException("need pass apk file");
        }
        if (argList.size() != 1) {
            throw new IllegalStateException("can not recognize args: " + StringUtils.join(argList));
        }
        ApkMeta originApkMeta;
        RuntimeBuilderContext zhenxiBuilderContext = new RuntimeBuilderContext();
        File inApkFile = new File(argList.get(0));
        if(!inApkFile.exists()){
            System.out.println("build apk not found ");
            throw new Exception(">>>>>>>>>>>> build apk not found "+inApkFile);
        }

        try (ApkFile apkFile = new ApkFile(inApkFile)) {
            originApkMeta = apkFile.getApkMeta();

            String originAPKManifestXml = apkFile.getManifestXml();
            ByteArrayInputStream byteArrayInputStream =
                    new ByteArrayInputStream(originAPKManifestXml.getBytes(StandardCharsets.UTF_8));
            zhenxiBuilderContext.originApplicationManifestDoc = loadDocument(byteArrayInputStream);

            byteArrayInputStream.close();
        }


        zhenxiBuilderContext.inApkFilePath = inApkFile.getPath();
        zhenxiBuilderContext.apkMeta = originApkMeta;

        zhenxiBuilderContext.inputZipFile = new ZipFile(inApkFile);
        try {
            parseManifest(inApkFile,zhenxiBuilderContext);
        }catch (Throwable e){
            e.printStackTrace();
        }

        List<String> axmlEditorCommand = new ArrayList<>();
        String AXML_EDITOR_CMD_PREFFIX = "ratel_axml_";
        for (String str : argList) {
            if (str.startsWith(AXML_EDITOR_CMD_PREFFIX)) {
                axmlEditorCommand.add(str.substring(AXML_EDITOR_CMD_PREFFIX.length()));
            }
        }
        zhenxiBuilderContext.axmlEditorCommand = axmlEditorCommand;


        return zhenxiBuilderContext;
    }

    void genMeta() throws IOException {
        if (cmd.hasOption('p')) {
            newPkgName = cmd.getOptionValue('p');
        }
        if (apkMeta.getPackageName().equals(newPkgName)) {
            throw new IllegalStateException("you should set a new package name,not: " + apkMeta.getPackageName());
        }

        //解析原始apk的applicationName
        Element applicationElement = (Element) originApplicationManifestDoc.getElementsByTagName("application").item(0);
        originApplicationClass = applicationElement.getAttribute("android:name");
        if (StringUtils.isBlank(originApplicationClass)) {
            originApplicationClass = applicationElement.getAttribute("name");
        }
        if (StringUtils.startsWith(originApplicationClass, ".")) {
            originApplicationClass = apkMeta.getPackageName() + originApplicationClass;
        }
        // Application可能没有.
        if (!StringUtils.contains(originApplicationClass, ".")) {
            originApplicationClass = apkMeta.getPackageName() + "." + originApplicationClass;
        }
        if ("android.app.Application".equals(originApplicationClass)) {
            //reset if use android default application class
            originApplicationClass = null;
        }
        //结尾以.结束 可能是不存在的application
        if(originApplicationClass!=null&&originApplicationClass.endsWith(".")){
            originApplicationClass = null;
        }

        System.out.println("xml origin application class:" + originApplicationClass);

        orignApkSupportArch = calculateAPKSupportArch(new File(inApkFilePath));
    }

    private static Set<String> calculateAPKSupportArch(File originAPK) throws IOException {
        Set<String> ret = Sets.newHashSet();
        //ret.add("armeabi");
        //ret.add("armeabi-v7a");

        try (ZipFile zipFile = new ZipFile(originAPK)) {
            for (ZipEntry zipEntry : zipFile.getEntries()) {
                if (zipEntry.getName().startsWith("lib/")) {
                    List<String> pathSegment = Splitter.on("/").splitToList(zipEntry.getName());
                    ret.add(pathSegment.get(1));
                }
            }
        }

        return ret;
    }

    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";
    private static final String FEATURE_LOAD_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String FEATURE_DISABLE_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";


    public static Document loadDocument(InputStream file)
            throws IOException, SAXException, ParserConfigurationException {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setFeature(FEATURE_DISABLE_DOCTYPE_DECL, true);
        docFactory.setFeature(FEATURE_LOAD_DTD, false);

        try {
            docFactory.setAttribute(ACCESS_EXTERNAL_DTD, " ");
            docFactory.setAttribute(ACCESS_EXTERNAL_SCHEMA, " ");
        } catch (IllegalArgumentException ex) {
            System.out.println("JAXP 1.5 Support is required to validate XML");
        }

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        // Not using the parse(File) method on purpose, so that we can control when
        // to close it. Somehow parse(File) does not seem to close the file in all cases.
        try {
            return docBuilder.parse(file);
        } finally {
            file.close();
        }
    }

    public void storezhenxiRPKGMeta(RuntimeBuilderContext zhenxiBuilderContext) throws IOException {
        //store build config data
        ZhenxiBuildProperties.setProperty("supportArch", Joiner.on(",").join(orignApkSupportArch));
        //add build serial number
        String serialNo = RunTimeConstants.RuntimePrefix + UUID.randomUUID().toString();
        System.out.println("build serialNo: " + serialNo);

        ZhenxiBuildProperties.setProperty(RunTimeConstants.KEY_zhenxi_BUILD_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        //ZhenxiBuildProperties.setProperty(Constants.KEY_zhenxi_BUILD_SERIAL, serialNo);

        ZhenxiBuildProperties.setProperty(RunTimeConstants.KEY_ORIGIN_PKG_NAME, apkMeta.getPackageName());
        CLog.i("originApplicationClass info -> "+originApplicationClass);
        //没找到的话,原来app不存在application的情况
        if(originApplicationClass==null||originApplicationClass.endsWith(".")){
            ZhenxiBuildProperties.setProperty(RunTimeConstants.KEY_ORIGIN_APPLICATION_NAME, "");
        }else {
            ZhenxiBuildProperties.setProperty(RunTimeConstants.KEY_ORIGIN_APPLICATION_NAME, originApplicationClass);
        }

        // 是否嵌入frida
        ZhenxiBuildProperties.setProperty(RunTimeConstants.IS_LOAD_FRIDA_GADGET, Boolean.toString(zhenxiBuilderContext.cmd.hasOption("f")));

        outPutZipMaker.putNextEntry("assets/" + RunTimeConstants.RUNTIME_CONFIG_PROPERTIES);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ZhenxiBuildProperties.store(byteArrayOutputStream, "auto generated by zhenxi repakcage builder!!!");
        outPutZipMaker.write(byteArrayOutputStream.toByteArray());
        byteArrayOutputStream.close();
    }


    private static void parseManifest(File originApk, RuntimeBuilderContext zhenxiBuilderContext) throws ParserConfigurationException, SAXException, IOException {

//        String originAPKManifestXml = ApkParsers.getManifestXml(originApk);
        Document document = zhenxiBuilderContext.originApplicationManifestDoc;

        Element manifestElement = (Element) document.getElementsByTagName("manifest").item(0);
        zhenxiBuilderContext.packageName = manifestElement.getAttribute("package");

        Element applicationElement = (Element) document.getElementsByTagName("application").item(0);
        zhenxiBuilderContext.appEntryClass = applicationElement.getAttribute("android:name");
        if (StringUtils.isBlank(zhenxiBuilderContext.appEntryClass)) {
            zhenxiBuilderContext.appEntryClass = applicationElement.getAttribute("name");
        }
        if (StringUtils.startsWith(zhenxiBuilderContext.appEntryClass, ".")) {
            zhenxiBuilderContext.appEntryClass = zhenxiBuilderContext.packageName + zhenxiBuilderContext.appEntryClass;
        }
        if ("android.app.Application".equals(zhenxiBuilderContext.appEntryClass)) {
            //reset if use android default application class
            zhenxiBuilderContext.appEntryClass = null;
        }

        zhenxiBuilderContext.androidAppComponentFactory = applicationElement.getAttribute("android:appComponentFactory");
        zhenxiBuilderContext.originApplicationClass = zhenxiBuilderContext.appEntryClass;
        zhenxiBuilderContext.launcherActivityClass = findLaunchActivityClass(applicationElement.getElementsByTagName("activity"));
        if (StringUtils.startsWith(zhenxiBuilderContext.launcherActivityClass, ".")) {
            zhenxiBuilderContext.launcherActivityClass = zhenxiBuilderContext.packageName + zhenxiBuilderContext.launcherActivityClass;
        }

        if (StringUtils.isBlank(zhenxiBuilderContext.appEntryClass)) {
            zhenxiBuilderContext.appEntryClass = zhenxiBuilderContext.launcherActivityClass;
        }

        ApkFile apkFile = new ApkFile(originApk);
        zhenxiBuilderContext.dexClassesMap = parseDexClasses(apkFile);
        zhenxiBuilderContext.appEntryClassDex = queryDexEntry(zhenxiBuilderContext.dexClassesMap, zhenxiBuilderContext.appEntryClass);

        if (zhenxiBuilderContext.appEntryClassDex == null && !StringUtils.contains(zhenxiBuilderContext.appEntryClass, ".")) {
            zhenxiBuilderContext.appEntryClassDex = queryDexEntry(zhenxiBuilderContext.dexClassesMap, zhenxiBuilderContext.packageName + "." + zhenxiBuilderContext.appEntryClass);
            if (zhenxiBuilderContext.appEntryClassDex != null) {
                zhenxiBuilderContext.appEntryClass = zhenxiBuilderContext.packageName + "." + zhenxiBuilderContext.appEntryClass;
            }
        }

    }


    private static String findLaunchActivityClass(NodeList activityNodeList) {
        //find launcher activity
        //String categoryInfoActivity = null;
        String categoryLauncherActivity = null;

        for (int i = 0; i < activityNodeList.getLength(); i++) {
            Node item = activityNodeList.item(i);
            if (!(item instanceof Element)) {
                continue;
            }
            Element activityElement = (Element) item;
            //intent filter 可能有多个
            NodeList intentFilterNodeList = activityElement.getElementsByTagName("intent-filter");
            if (intentFilterNodeList == null || intentFilterNodeList.getLength() == 0) {
                continue;
            }
            for (int j = 0; j < intentFilterNodeList.getLength(); j++) {
                Node item1 = intentFilterNodeList.item(j);
                if (!(item1 instanceof Element)) {
                    continue;
                }
                Element intentFilterElement = (Element) item1;
                NodeList actionNodeList = intentFilterElement.getElementsByTagName("action");
                boolean hint = false;
                for (int k = 0; k < actionNodeList.getLength(); k++) {
                    Node item2 = actionNodeList.item(k);
                    if (!(item2 instanceof Element)) {
                        continue;
                    }
                    Element actionElement = (Element) item2;
                    if ("android.intent.action.MAIN".equals(actionElement.getAttribute("android:name"))) {
                        hint = true;
                        break;
                    }
                }
                if (!hint) {
                    continue;
                }

                // hint android.intent.action.MAIN
                // first step try android.intent.category.INFO then retry with android.intent.category.LAUNCHER

                NodeList categoryNodeList = intentFilterElement.getElementsByTagName("category");
                for (int k = 0; k < categoryNodeList.getLength(); k++) {
                    Node item2 = categoryNodeList.item(k);
                    if (!(item2 instanceof Element)) {
                        continue;
                    }
                    Element categoryElement = (Element) item2;
//                    if ("android.intent.category.INFO".equals(categoryElement.getAttribute("android:name"))) {
//                        categoryInfoActivity = activityElement.getAttribute("android:name");
//                        break;
//                    } else
                    if ("android.intent.category.LAUNCHER".equals(categoryElement.getAttribute("android:name"))) {
                        return activityElement.getAttribute("android:name");

                    }
                }

            }
        }
        return null;
    }

    private static Map<String, DexClass[]> parseDexClasses(ApkFile apkFile) throws IOException {
        Map<String, DexClass[]> ret = Maps.newHashMap();

        byte[] firstDex = apkFile.getFileData(AndroidConstants.DEX_FILE);
        if (firstDex == null) {
            String msg = String.format("Dex file %s not found", AndroidConstants.DEX_FILE);
            System.out.println("build apk not find dex ");
            return ret;
        }
        ByteBuffer buffer = ByteBuffer.wrap(firstDex);
        DexParser dexParser = new DexParser(buffer);
        DexClass[] parse = dexParser.parse();
        ret.put(AndroidConstants.DEX_FILE, parse);


        for (int i = 2; i < 1000; i++) {
            try {
                String path = String.format(Locale.ENGLISH, AndroidConstants.DEX_ADDITIONAL, i);
                byte[] fileData = apkFile.getFileData(path);
                if (fileData == null) {
                    break;
                }
                buffer = ByteBuffer.wrap(fileData);
                dexParser = new DexParser(buffer);
                parse = dexParser.parse();
                ret.put(path, parse);
            } catch (Exception e) {
                //ignore because of entry point class can not be encrypted
            }
        }
        return ret;
    }

    private static String queryDexEntry(Map<String, DexClass[]> dexClassesMap, String targetClassName) {
        if (StringUtils.isBlank(targetClassName)) {
            return null;
        }
        for (Map.Entry<String, DexClass[]> entry : dexClassesMap.entrySet()) {
            String dexPath = entry.getKey();
            DexClass[] classes = entry.getValue();
            for (DexClass dexClass : classes) {
                String className = Util.descriptorToDot(dexClass.getClassType());
                if (className.equals(targetClassName)) {
                    return dexPath;
                }
            }
        }
        return null;
    }
}

package com.zhenxi.builder;

import com.beust.jcommander.internal.Sets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class DexSplitter {
    static File splitDex(File originDex, File workDir, Set<String> remainClasses, RuntimeBuilderContext context) {
        //在AndroidMainfest.xml中取出关键的类
        Set<String> classes = extractClasses(context);
        classes.add(context.appEntryClass);
        if (StringUtils.isNotBlank(context.androidAppComponentFactory)) {
            classes.add(context.androidAppComponentFactory);
        }
        if(remainClasses != null){
            classes.addAll(remainClasses);
        }

        try {
            DexBackedDexFile dexBackedDexFile = new DexBackedDexFile(Opcodes.getDefault(), FileUtils.readFileToByteArray(originDex));// DexFileFactory.loadDexFile(originDex, Opcodes.getDefault());
            Set<? extends DexBackedClassDef> allClasses = dexBackedDexFile.getClasses();
            //取出dex文件中所有的类内容
            Map<String, ClassDef> classDefMap = new HashMap<>();
            for (ClassDef classDef : allClasses) {
                classDefMap.put(classDef.getType(), classDef);
            }


            DexPool mainDexPool = new DexPool(Opcodes.getDefault());

            DexPool otherDexPool = new DexPool(Opcodes.getDefault());


            Set<ClassDef> mainDexClassDefs = new CopyOnWriteArraySet<>();

            ClassDef entryClassDef = null;
            //找到dex文件中包含的关键类信息和入口类信息，但是可能找不全
            for (ClassDef classDef : allClasses) {
                if (classes.contains(Util.descriptorToDot(classDef.getType()))) {
                    mainDexClassDefs.add(classDef);
                }
                if (Util.descriptorToDot(classDef.getType()).equals(context.appEntryClass)) {
                    entryClassDef = classDef;
                }
            }

            //首先做一次application的supper class合并，保证application能够放到同一个dex
            if (entryClassDef != null) {
                Set<String> supperClass = new HashSet<>();
                extractSuperClass(entryClassDef, supperClass, classDefMap);
                for (String supperClassDefStr : supperClass) {
                    mainDexClassDefs.add(classDefMap.get(supperClassDefStr));
                }
            }


            //根据优先级规则，将dex拆分为两部分，使得的一个dex的代码在安装后最容易被执行
            moveDef(mainDexClassDefs, classDefMap, context.packageName);

            for (ClassDef classDef : allClasses) {
                if (mainDexClassDefs.contains(classDef)) {
                    mainDexPool.internClass(classDef);
                } else {
                    otherDexPool.internClass(classDef);
                }
            }

            // otherDexPool需要加入特殊标记，否则会在下次build的时候，和addOnDexMerge冲突
            // 因为addsOnDex就是依靠文件名称判定的
            otherDexPool.internClass(createSplitterFeatureFlagClass());

            FileDataStore originDexFileDataStore = new FileDataStore(originDex);
            mainDexPool.writeTo(originDexFileDataStore);
            originDexFileDataStore.close();

            File overflowDexFile = new File(workDir, "overflow.dex");

            FileDataStore splitDexFileDataStore = new FileDataStore(overflowDexFile);

            otherDexPool.writeTo(splitDexFileDataStore);
            splitDexFileDataStore.close();
            return overflowDexFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }


    }

    public static final String SplitterDexFlagClassPreffix = "com.virjar.ratel.SplitDexFlag";

    public static boolean isRatelSplitDex(String type) {
        return Util.descriptorToDot(type).startsWith(SplitterDexFlagClassPreffix);
    }

    private static ClassDef createSplitterFeatureFlagClass() {
        byte[] bytes = AndroidJarUtil.genRandomHelloWorldClass(SplitterDexFlagClassPreffix);
        DexBackedDexFile dexBackedDexFile = new DexBackedDexFile(Opcodes.getDefault(), bytes);
        for (ClassDef classDef : dexBackedDexFile.getClasses()) {
            String className = Util.descriptorToDot(classDef.getType());
            if (className.startsWith(SplitterDexFlagClassPreffix)) {
                return classDef;
            }
        }
        throw new IllegalStateException("can not create SplitterFeatureFlagClass: " + SplitterDexFlagClassPreffix);
    }


    private static final double barrie = 0.5D;

    private static void moveDef(Set<ClassDef> mainDexClassDefs, Map<String, ClassDef> classDefMap, String appPackageName) {
        if (mainDexClassDefs.size() >= classDefMap.size() * barrie) {
            return;
        }
        //合并所有的父class，包括interface
        for (ClassDef classDef : mainDexClassDefs) {
            Set<String> supperClass = new HashSet<>();
            extractSuperClass(classDef, supperClass, classDefMap);
            if (supperClass.size() == 0) {
                continue;
            }
            for (String supperClassDefStr : supperClass) {
                mainDexClassDefs.add(classDefMap.get(supperClassDefStr));
            }
            if (mainDexClassDefs.size() >= classDefMap.size() * barrie) {
                return;
            }
        }

        //合并属性依赖,
        Set<String> accessedClasses = Sets.newHashSet();
        Set<String> nowLoop = new HashSet<>();
        for (ClassDef classDef : mainDexClassDefs) {
            for (Field field : classDef.getFields()) {
                String classType = field.getType();
                if (classDefMap.containsKey(classType)) {
                    nowLoop.add(classType);
                    accessedClasses.add(classType);
                }
            }
        }
        //最多合并5层
        for (int i = 0; i < 5; i++) {
            Set<String> nextLoop = Sets.newHashSet();
            for (String refField : nowLoop) {
                ClassDef classDef = classDefMap.get(refField);
                if (mainDexClassDefs.contains(classDef)) {
                    continue;
                }

                mainDexClassDefs.add(classDef);
                if (mainDexClassDefs.size() >= classDefMap.size() * barrie) {
                    return;
                }
                for (Field field : classDef.getFields()) {
                    String classType = field.getType();
                    if (classDefMap.containsKey(classType) && !accessedClasses.contains(classType)) {
                        nextLoop.add(classType);
                        accessedClasses.add(classType);
                    }
                }
            }
            nowLoop = nextLoop;
        }

        //合并内部类
        Set<ClassDef> nowInnerClassTask = Sets.newHashSet();
        nowInnerClassTask.addAll(mainDexClassDefs);
        for (int i = 0; i < 5; i++) {
            Set<ClassDef> nextInnerClassTask = Sets.newHashSet();

            for (ClassDef classDef : nowInnerClassTask) {
                if (mainDexClassDefs.contains(classDef)) {
                    continue;
                }
                String classType = classDef.getType();
                for (ClassDef mClassDef : mainDexClassDefs) {
                    String parent = mClassDef.getType();
                    if (!classType.startsWith(parent)) {
                        continue;
                    }

                    String className = classType.substring(parent.length());
                    if (className.startsWith("$")) {
                        mainDexClassDefs.add(classDef);
                        nextInnerClassTask.add(classDef);
                    }
                }
            }

            nowInnerClassTask = nextInnerClassTask;
        }


        //合并同包名下的代码
        for (ClassDef classDef : classDefMap.values()) {
            if (mainDexClassDefs.contains(classDef)) {
                continue;
            }
            String className = Util.descriptorToDot(classDef.getType());
            if (className.startsWith(appPackageName)
                    || className.startsWith("android.")) {
                mainDexClassDefs.add(classDef);
                if (mainDexClassDefs.size() >= classDefMap.size() * barrie) {
                    return;
                }
            }
        }
    }


    private static void extractSuperClass(ClassDef classDef, Set<String> supperClass, Map<String, ClassDef> classDefMap) {
        String superclass = classDef.getSuperclass();

        if (superclass != null && classDefMap.containsKey(superclass)) {
            supperClass.add(superclass);
            extractSuperClass(classDefMap.get(superclass), supperClass, classDefMap);
        }


        for (String interfaceStr : classDef.getInterfaces()) {
            ClassDef interfaceClassDef = classDefMap.get(interfaceStr);
            if (interfaceClassDef != null) {
                supperClass.add(interfaceStr);
                extractSuperClass(interfaceClassDef, supperClass, classDefMap);
            }
        }


    }


    private static Set<String> extractClasses(RuntimeBuilderContext context) {
        Document androidManifestXml = context.originApplicationManifestDoc;
        Set<String> classes = Sets.newHashSet();
        classes.addAll(extractClassByXPath(androidManifestXml, "/manifest/application/activity", context.packageName));
        classes.addAll(extractClassByXPath(androidManifestXml, "/manifest/application/provider", context.packageName));
        classes.addAll(extractClassByXPath(androidManifestXml, "/manifest/application/service", context.packageName));
        classes.addAll(extractClassByXPath(androidManifestXml, "/manifest/application/receiver", context.packageName));
        return classes;
    }


    private static Set<String> extractClassByXPath(Document androidManifestXml, String xpath, String packageName) {
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            XPathExpression expression = xPath.compile(xpath);
            NodeList nodes = (NodeList) expression.evaluate(androidManifestXml, XPathConstants.NODESET);

            Set<String> ret = Sets.newHashSet();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                NamedNodeMap attrs = node.getAttributes();

                if (attrs != null) {
                    Node classNameNode = attrs.getNamedItem("android:name");
                    if (classNameNode == null) {
                        continue;
                    }
                    String className = classNameNode.getNodeValue();
                    if (!className.startsWith(".")) {
                        ret.add(className);
                    } else {
                        ret.add(packageName + className);
                    }
                }
            }
            return ret;
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }

    }
}

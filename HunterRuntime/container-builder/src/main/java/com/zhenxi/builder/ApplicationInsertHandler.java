package com.zhenxi.builder;

import com.zhenxi.meditor.utils.CLog;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jf.baksmali.DexInputCommand;
import org.jf.baksmali.Main;
import org.jf.util.ClassFileNameHandler;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ApplicationInsertHandler {

    /**
     * 将 Java规范的类限定名转换成smali中/分割 的限定名
     *
     * @param javaClassName com.stub.StubApp
     * @return Lcom/stub/StubApp;
     */
    private static String convertJavaClassNameToSmali(String javaClassName) {
        return "L" + javaClassName.replace(".", "/") + ";";
    }


    /**
     * 判断dex文件中是否包含某个class
     *
     * @param dexFile     dex文件
     * @param targetClass 目标class,Java限定名形式
     * @return contains
     */
    public static boolean hasClass(File dexFile, String targetClass) {
        if (dexFile == null || StringUtils.isBlank(targetClass)) {
            return false;
        }
        if(!dexFile.exists()){
            System.out.println("hasClass dex not found !!!!!");
            System.exit(0);
        }
        List<String> classes = DexInputCommand.getDexClass(dexFile.getAbsolutePath());
        return classes.contains(convertJavaClassNameToSmali(targetClass));
    }

    public static boolean hasClassBySmaliName(File dexFile, String targetClass) {
        if (dexFile == null || StringUtils.isBlank(targetClass)) {
            return false;
        }
        if (!dexFile.exists()) {
            System.out.println("hasClassBySmaliName dex not found !!!!!");
            System.exit(0);
        }
        List<String> classes = DexInputCommand.getDexClass(dexFile.getAbsolutePath());
        return classes.contains(targetClass);
    }


    public static boolean hasMethod(File dexFile, String targetClass, String targetMethod) {
        if (dexFile == null || StringUtils.isBlank(targetMethod)) {
            return false;
        }
        if (!dexFile.exists()) {
            System.out.println("hasMethod dex not found !!!!!");
            System.exit(0);
        }
        List<String> methods = DexInputCommand.getMethods(dexFile.getAbsolutePath());
        String method = convertJavaClassNameToSmali(targetClass) + "->" + targetMethod;
        return methods.contains(method);
    }

    public static boolean hasClinitMethod(File dexFile, String targetClass) {
        return hasMethod(dexFile, targetClass, "<clinit>()V");
    }


    public static File disassembleSmaliFromDex(File dexFile, String targetClass, String resultDir) {
        if (StringUtils.isBlank(targetClass)) {
            return null;
        }
        String smaliClassName =
                targetClass.startsWith("L") ? targetClass : convertJavaClassNameToSmali(targetClass);
        Main.main(new String[]{"d", "--classes", smaliClassName, dexFile.getAbsolutePath(), "-o", resultDir});
        return new ClassFileNameHandler(new File(resultDir), ".smali").getUniqueFilenameForClass(smaliClassName);
    }


    public static class ApplicationPair {
        public File smaliFile;
        public File dexFile;


        public ApplicationPair(File smaliFile, File dexFile) {
            this.smaliFile = smaliFile;
            this.dexFile = dexFile;
        }

        @Override
        public String toString() {
            return "ApplicationPair{" +
                    "smaliFile=" + smaliFile +
                    ", dexFile=" + dexFile +
                    '}';
        }
    }


    private boolean isMatch(File smaliFile) {
        try {
            List<String> lines = FileUtils.readLines(smaliFile, StandardCharsets.UTF_8);
            for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
                String line = lines.get(lineNo);
                // 判断是否是Application的子类，若不是递归向父类查找
                if (line.startsWith(".super")) {
                    String superClassName = line.split(".super ")[1];
                    if (superClassName.equals("Landroid/app/Application;")) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("findParentApplication->isMatch "+e);
        }
        return false;
    }

    /**
     * 查找目标Application是否存在父类，找出最顶层Application所在的smali和dex
     *
     * @param smaliFile 原始Application的smali文件
     * @param dexFile 原始Application所在的dex文件
     */
    public static ApplicationPair findParentApplication(File smaliFile,
                                                        File dexFile,File workDirFile,
                                                        String file_prefix,String topApplicationInfo
    ) throws IOException {
        if (!smaliFile.exists()) {
            //Log.e("findParentApplication smaliFile not exists " + smaliFile.getPath());
            return null;
        }
        List<String> lines = FileUtils.readLines(smaliFile, StandardCharsets.UTF_8);
        for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
            String line = lines.get(lineNo);
            // 判断是否是Application的子类，若不是递归向父类查找
            if (line.startsWith(".super")) {
                String superClassName = line.split(".super ")[1];
                if (!superClassName.equals(topApplicationInfo)) {
                    // 父类有可能不在同一个DEX中
                    File[] allDexFiles = workDirFile.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File file, String s) {
                            return s.startsWith(file_prefix) && s.endsWith(".dex");
                        }
                    });
                    File targetDex = dexFile;
                    // 查找所存在的DEX
                    if (allDexFiles != null) {
                        for (File dex : allDexFiles) {
                            if (hasClassBySmaliName(dex, superClassName)) {
                                targetDex = dex;
                            }
                        }
                    }
                    File superSmaliFile = disassembleSmaliFromDex(targetDex, superClassName, workDirFile.getPath());
                    return findParentApplication(superSmaliFile, targetDex, workDirFile,file_prefix,topApplicationInfo);
                } else {
                    return new ApplicationPair(smaliFile, dexFile);
                }

            }
        }
        throw new IllegalArgumentException("target smali  file is not a Application:" + smaliFile.getAbsolutePath());
    }

    private static final String intoSmali = "    invoke-static {}, Lcom/hunter/runtime/HunterRuntime;->startFromStaticMethod()V\n";

    public static File reBuildSmali(File smaliFile, boolean insertMode, String resultDir, String findTopApplication) throws IOException {
        if (smaliFile == null||!smaliFile.exists()) {
            System.out.println("add smali code , the smali to rebuild is empty!");
            return null;
        }
        // 解析smali文件
        List<String> lines = FileUtils.readLines(smaliFile, StandardCharsets.UTF_8);
        for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
            String line = lines.get(lineNo);
            // 判断是否是Application的子类，若不是递归向父类查找
            if (line.startsWith(".super")) {
                String superClassName = line.split(".super ")[1];
                if (!superClassName.equals(findTopApplication)) {
                    throw new IllegalArgumentException("current smali is not the earliest application");
                }
            }

            // 如果有静态代码块，直接追加修改
            if (line.contains("constructor <clinit>()V")) {
                CLog.i(">>>>>> find static code ");
                // hexl-add dex重编的时候，静态代码块注册的寄存器至少要是1
                String params = lines.remove(lineNo + 1);
                if (params.contains(".registers 0")) {
                    params = params.replace(".registers 0", ".registers 1");
                }
                lines.add(lineNo + 1, params);

                lines.add(lineNo + 1, "\r\n");
                String smaliCode = "\n" +
                        "\n" +
                        intoSmali;
                lines.add(lineNo + 2, smaliCode);
                lines.add(lineNo + 3, "\r\n");
                break;
            }

            // 如果没有静态代码块，找个方法结束插入静态代码块
            if (insertMode && line.contains(".end method")) {
                CLog.i(">>>>>> not find static code ");
                String insertSmali = "# direct methods\n" +
                        ".method static constructor <clinit>()V\n" +
                        "    .registers 1\n" +
                        "\n" +
                        intoSmali +
                        "\n" +
                        "    return-void\n" +
                        ".end method";
                lines.add(lineNo + 1, "\r\n");
                lines.add(lineNo + 2, insertSmali);
                lines.add(lineNo + 3, "\r\n");
                break;
            }
        }

        // output to a new file
        File newSmali = new File(resultDir, "new_" + smaliFile.getName());
        CLog.e("rebuilder dex file path -> "+newSmali);
        FileUtils.writeLines(newSmali, "UTF-8", lines, false);
        return newSmali;
    }
}

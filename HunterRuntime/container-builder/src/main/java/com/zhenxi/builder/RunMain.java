package com.zhenxi.builder;

import static com.zhenxi.builder.ContainerBuilder.mBuilderContext;

import com.google.common.base.Joiner;
import com.zhenxi.meditor.utils.CLog;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.File;
import java.text.DateFormat;
import java.util.List;
import java.util.Locale;

import bin.zip.ZipEntry;

/**
 * @author Zhenxi on 2025/1/20
 */
public class RunMain {


    public static void main(String[] args) throws Exception {

        Options options = new Options();
        options.addOption(new Option("w", "workdir", true, "set a zhenxi working dir"));
        options.addOption(new Option("t", "tell", false, "tell me the output apk file path"));
        options.addOption(new Option("h", "help", false, "print help message"));
        options.addOption(new Option("o", "output", true, "the output apk path or output project path"));
        options.addOption(new Option("s", "signature", false, "signature apk with zhenxi default KeyStore"));
        options.addOption(new Option("d", "debug", false, "add debuggable flag on androidManifest.xml "));
        options.addOption(new Option("dex", "dex", false, "use dex rebuild to repackage"));
        options.addOption(new Option("f", "frida", false, "embed frida-gadget.so"));
        options.addOption(new Option("factory", "factory", false, "replace AppComponentFactory name"));
        options.addOption(new Option("extract", "extract", false, "if find extractNativeLibs set true"));
        options.addOption(new Option("removeiso", "removeiso", false, "remove isolatedProcess check"));
        options.addOption(new Option("removezygote", "removezygote", false, "replace zygotePreloadName name"));
        options.addOption(new Option("r", "install", false, "rebuid & install -r apk file"));
        options.addOption(new Option("removeHasCode", "removeHasCode", false, "remove application android:hasCode=false"));
        options.addOption(new Option("target", "SdkVersion", true, "replace application targetSdkVersion"));
        options.addOption(new Option("rw_sdcard", "WRITE_SDCARD&READ_SDCARD", false, "add read & wirte sdcard"));
        options.addOption(new Option("sign_path", "app sign apk path ", true, "sign apk path"));

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args, false);

        if (cmd.hasOption('t')) {
            System.out.println(mBuilderContext.outApkFilePath);
            return;
        }
        ContainerBuilder.cleanWorkDir();
        ContainerBuilder.initBuilder(cmd,options);

        System.out.println("build param: " + Joiner.on(" ").join(args));
        ContainerBuilder.initApkSign();

        //将EngineApk拷贝到工作目录
        ContainerBuilder.copyEngineApkToWorkDir();
        //把输入的apk文件拷贝到输出apk里面
        //获取原始apk全部的dex文件,保存到list里面,计算dexAppendIndex
        //dexEntries保存了原始dex文件,这是的apk里面应该没有dex和xml文件,后续插入
        List<ZipEntry> dexEntries = ContainerBuilder.copyInPutApkFile(mBuilderContext);
        //开始处理dex,如果是dex插入直接
        ContainerBuilder.migrateBasicFile(cmd, mBuilderContext, dexEntries);

        //打入原始apk
        ContainerBuilder.injectFile();

        //manifest 文件需要修改
        ContainerBuilder.editManifest(mBuilderContext);
        //这是out.apk只有自己的重构的dex文件,开始植入EngineApk外置so,dex文件
        ContainerBuilder.injectzhenxiResource(mBuilderContext);
        //相关构建信息存储到特殊文件,记录感染的一些信息。
        mBuilderContext.storezhenxiRPKGMeta(mBuilderContext);


        mBuilderContext.inputZipFile.close();
        mBuilderContext.outPutZipMaker.close();

        CLog.e("app sign before app size "+new File(mBuilderContext.outApkFilePath).length());

        //签名apk
        ContainerBuilder.signOutPutApk(cmd, mBuilderContext.outApkFilePath);

        CLog.e("app sign after app size "+new File(mBuilderContext.outApkFilePath).length());

        //将没用的流占用gc掉,防止被占用导致删除不了
        System.gc();
        //清理工作目录
        ContainerBuilder.cleanWorkingDir();
        //
        ContainerBuilder.installBuilderApk(new File(mBuilderContext.outApkFilePath));
        System.err.println(">>>>>>>>>>>  build finsh " +
                DateFormat.getDateTimeInstance(DateFormat.DEFAULT,
                        DateFormat.DEFAULT, Locale.CHINA).format(System.currentTimeMillis()) +
                "  \n" + mBuilderContext.inApkFilePath +" -> "+ mBuilderContext.outApkFilePath
        );
    }
}

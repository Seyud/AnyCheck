package com.hunter.external.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * @author Zhenxi on 2022/10/16
 */
public class FileUtils {
    public static File makeSureDirExist(File file) {

        if (file.getPath().equals("")) {
            return file;
        }
        if (file.exists() && file.isFile()) {
            return file;
        }

        boolean mkdirs = file.mkdirs();
        if(mkdirs) {
            try {
                boolean a = file.setExecutable(true, false);
                boolean b = file.setWritable(true, false);
                boolean c = file.setReadable(true, false);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        return file;
    }

    private static FileOutputStream outStream = null;
    private static OutputStreamWriter writer = null;
    /*
     * 用于一个文件保存大量字符串
     * 不进行关闭,防止反复创建影响效率
     */
    public static void saveStringNoClose(Context mContext, String str, File file) {
        try {
            if(str==null){
                return;
            }
            //CLog.e("save str info -> "+str);
            if(!file.exists()){
                boolean newFile = file.createNewFile();
            }
            if (outStream == null) {
                outStream = new FileOutputStream(file, true);
                writer = new OutputStreamWriter(outStream, StandardCharsets.UTF_8);
            }
            writer.write(str);
            writer.flush();
        } catch (Throwable e) {
            ToastUtils.showToast(mContext,"Please check the sdcard read and write permissions : " + e.getMessage());
        }
    }


    public static void saveStringNoCloseDestroy(){
        try {
            if(outStream !=null){
                outStream.close();
            }
            if(writer !=null){
                writer.close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }finally {
            try {
                if(outStream !=null){
                    outStream.close();
                }
                if(writer !=null){
                    writer.close();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }


}

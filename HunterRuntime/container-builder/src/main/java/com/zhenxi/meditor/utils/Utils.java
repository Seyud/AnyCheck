package com.zhenxi.meditor.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Utils {
    private static final byte[] BUFFER = new byte[4194304];

    public Utils() {
    }

    public static byte[] getBytesFromFile(String pathStr) {
        File file = new File(pathStr);
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(file);
            byte[] var3 = getBytesFromInputStream(fis);
            return var3;
        } catch (Exception var7) {
            var7.printStackTrace();
        } finally {
            close(fis);
        }

        return null;
    }

    public static byte[] getBytesFromInputStream(InputStream inputStream) {
        ByteArrayOutputStream bos = null;

        try {
            bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];

            int n;
            while ((n = inputStream.read(b)) != -1) {
                bos.write(b, 0, n);
            }

            byte[] data = bos.toByteArray();
            byte[] var5 = data;
            return var5;
        } catch (Exception var9) {
            var9.printStackTrace();
        } finally {
            close(bos);
        }

        return null;
    }

    public static void writeBytesToFile(byte[] bytes, String filePath) {
        FileOutputStream fileOuputStream = null;

        try {
            fileOuputStream = new FileOutputStream(filePath);
            fileOuputStream.write(bytes);
        } catch (IOException var7) {
            var7.printStackTrace();
        } finally {
            close(fileOuputStream);
        }

    }

    public static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException var2) {
            var2.printStackTrace();
        }

    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isEqual(String str1, String str2) {
        if (str1 == null) {
            return str2 == null;
        } else {
            return str1.equals(str2);
        }
    }

    public static boolean isAndroidNamespace(String str) {
        return "http://schemas.android.com/apk/res/android".equals(str);
    }

    public static void copyStream(InputStream input, OutputStream output) throws IOException {
        int bytesRead;
        while ((bytesRead = input.read(BUFFER)) != -1) {
            output.write(BUFFER, 0, bytesRead);
        }

    }

    public static InputStream getInputStreamFromFile(String filePath) {
        return Utils.class.getClassLoader().getResourceAsStream(filePath);
    }

    public static void copyFileFromJar(String inJarPath, String distPath) {
        Log.d("start copyFile  inJarPath =" + inJarPath + "\n  distPath = " + distPath);
        InputStream inputStream = getInputStreamFromFile(inJarPath);
        BufferedInputStream in = null;
        BufferedOutputStream out = null;

        try {
            in = new BufferedInputStream(inputStream);
            out = new BufferedOutputStream(new FileOutputStream(distPath));
            byte[] b = new byte[1024];

            int len;
            while ((len = in.read(b)) != -1) {
                out.write(b, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(out);
            close(in);
        }

    }
}

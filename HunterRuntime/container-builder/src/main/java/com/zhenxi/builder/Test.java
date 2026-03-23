package com.zhenxi.builder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Test {
    public static String test_path = "/Users/didi/Desktop/AutoTest/test_file";

    public static void main(String[] args) {
        File file = new File(test_path);
        System.out.println("目标文件路径: " + file.getAbsolutePath());

        try (FileWriter fw = new FileWriter(file)) {
            fw.write("这是一个写入测试。");
            System.out.println("写入成功！");
        } catch (IOException e) {
            System.err.println("写入失败: " + e.getMessage());
        }
    }
}

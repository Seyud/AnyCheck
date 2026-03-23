package com.runtime.magisk;

import androidx.annotation.NonNull;


/**
 * @author Zhenxi on 2023/12/5
 * 客户端向服务端发送请求设备的基础Bean对象
 */
public class RequestInitBean {
    /**
     * client package name
     */
    public String initFpPackageName;
    /**
     * client uid
     */
    public int Uid ;
    /**
     * user id
     * /data/user/user_id/
     */
    public int user_id = 0;


    public String now_user;

    @NonNull
    @Override
    public String toString() {
        return "{" +
                "initFpPackageName='" + initFpPackageName + '\'' +
                ", Uid=" + Uid +
                ", now_user='" + now_user + '\'' +
                '}';
    }
}

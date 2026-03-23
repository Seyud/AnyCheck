package com.runtime.magisk;

import android.content.Context;
import android.os.Build;

import com.runtime.magisk.server.RuntimeManagerService;
import com.runtime.magisk.utils.CLog;
import com.runtime.magisk.utils.HiddenAPIEnforcementPolicyUtils;


/**
 * @author Zhenxi on 2023/11/30
 */
public class RuntimeServiceInit {

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenAPIEnforcementPolicyUtils.passApiCheck();
        }
    }


    /**
     * 初始化服务端逻辑
     *
     * @param rms RuntimeManagerService
     */
    public static void RuntimeServiceInitStart(RuntimeManagerService rms,
                                               RequestInitBean bean) {
        try {
            CLog.i("RuntimeServiceInitStart start init "+bean.toString());


        } catch (Throwable e) {
            CLog.e("RuntimeServiceInitStart error " + e.getMessage(), e);
        }
    }


}

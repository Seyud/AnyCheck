package com.hunter.external.utils;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by fullcircle on 2016/12/31.
 */

public class ToastUtils {

    private static Toast toast;

    public static void showToast(Context context,String msg){
        showToast(context,msg,false);
    }

    public static void showToast(Context context,String msg,boolean isLong){
        ThreadUtils.runOnMainThread(() -> {
            if(toast==null){
                //如果toast第一次创建 makeText创建toast对象
                toast = Toast.makeText(context.getApplicationContext(),
                        msg,isLong?Toast.LENGTH_LONG:Toast.LENGTH_SHORT);
            }else{
                //如果toast存在 只需要修改文字
                toast.setText(msg);
            }
            toast.show();
        });
    }
}

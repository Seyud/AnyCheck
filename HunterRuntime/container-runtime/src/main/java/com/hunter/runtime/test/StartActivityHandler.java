package com.hunter.runtime.test;

import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.hunter.external.extend.ChooseUtils;
import com.hunter.external.extend.superappium.PageTriggerManager;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.utils.CLog;
import com.hunter.external.utils.CommandExecution;
import com.hunter.external.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Zhenxi on 2022/10/22
 */
public class StartActivityHandler implements PageTriggerManager.ActivityFocusHandler {

    @Override
    public boolean handleActivity(Activity activity, ViewImage root) {
//        CLog.e("开始执行handleActivity "+activity.getClass().getName());
//        CLog.e("handleActivity toString "+root.toString());
//
//        ArrayList<TextView> isRead = ChooseUtils.choose(TextView.class, true);
//        for(TextView textView:isRead){
//            if(textView.getText().toString().contains("我已阅读并同意")){
//                textView.callOnClick();
//                CLog.e("点击我已阅读成功！");
//            }
//        }
//        ArrayList<TextView> choose = ChooseUtils.choose(TextView.class, true);
//        for(TextView view:choose){
//            if(view.getText().toString().contains("手机验证码")){
//                CLog.i("view 内容 "+view.getText());

//                ViewImage image = new ViewImage(textView);
//                boolean click = image.click();
//                CLog.e("发现手机验证码TextView "+view.callOnClick());

//                float[] floats = {172, 68};
//                long downTime = SystemClock.uptimeMillis();
//                view.onTouchEvent(ViewImage.genMotionEvent(downTime,MotionEvent.ACTION_DOWN,
//                        floats,4,0x20000));
//                ThreadUtils.runOnMainThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        view.onTouchEvent(ViewImage.genMotionEvent(downTime,
//                                MotionEvent.ACTION_UP,floats,4,0x20000));
//                    }
//                },80);



//                String cmd = "input tap 521 1598";
//                CommandExecution.execCommand(cmd,true);
//                CLog.e("root 点击执行完毕 ");
//            }
//        }
//        CLog.e("handleActivity 执行完毕 ");
        return true;
    }

    @Override
    public void onRetryFailed(Activity activity, ViewImage root) {

    }
}

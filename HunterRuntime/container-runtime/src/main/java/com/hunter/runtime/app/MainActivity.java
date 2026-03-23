package com.hunter.runtime.app;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.hunter.external.utils.CLog;


/**
 * @author zhenxi on 2022/1/7
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CLog.e(">>>>>>>>>>>>> MainActivity-> onCreate ");
    }
}

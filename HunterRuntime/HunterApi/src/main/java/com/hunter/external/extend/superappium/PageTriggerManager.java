package com.hunter.external.extend.superappium;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.PopupWindow;


import com.hunter.api.rposed.RC_MethodHook;
import com.hunter.api.rposed.RposedBridge;
import com.hunter.api.rposed.RposedHelpers;
import com.hunter.external.utils.AppUtils;
import com.hunter.external.utils.CLog;
import com.hunter.external.utils.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import external.org.apache.commons.lang3.StringUtils;


/**
 * 基于页面实现控制逻辑抽象，包括activity和fragment两个维度
 */
public class PageTriggerManager {

    private static boolean isInit = false ;
    /**
     * 初始化
     */
    public static void init (ClassLoader classLoader){
        if(isInit){
            return;
        }
        enablePageMonitor(classLoader);
        isInit = true;
    }


    public interface ActivityFocusHandler {
        boolean handleActivity(Activity activity, ViewImage root);

        void onRetryFailed(Activity activity, ViewImage root);
    }

    public interface FragmentFocusHandler {
        boolean handleFragmentPage(Object fragment, Activity activity, ViewImage root);

        void onRetryFailed(Object fragment, Activity activity, ViewImage root);
    }

    /**
     * 与ActivityFocusHandler机制不同，dialog无法通过类名简单区分，
     * 因此每个dialog show时均会触发所有DialogFocusHandler
     */
    public interface DialogFocusHandler {
        boolean handleDialog(Dialog dialog, ViewImage root);
    }

    private static final HashMap<String, ActivityFocusHandler> activityFocusHandlerMap = new HashMap<>();

    private static final HashMap<String, FragmentFocusHandler> fragmentFocusHandlerHashMap = new HashMap<>();

    private static final Set<DialogFocusHandler> dialogFocusHandlers = new HashSet<>();

    private static final Handler mainLooperHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("StaticFieldLeak")
    private volatile static Activity topActivity = null;

    private static final Map<String, Object> topFragmentMaps = new ConcurrentHashMap<>();


    private static int taskDuration = 200;

    private static boolean hasPendingActivityTask = false;

    private static boolean disable = false;

//    private static final Set<WeakReference<LocalActivityManager>> localActivityManagers = new CopyOnWriteArraySet<>();

    private static final Set<WeakReference<Window>> dialogWindowsSets = new CopyOnWriteArraySet<>();

    private static final Set<WeakReference<PopupWindow>> popupWindowSets = new CopyOnWriteArraySet<>();

    /**
     * 设置任务时间间隔，这会影响case执行速度
     *
     * @param taskDuration case间隔时间，默认200毫秒，也就是0.2秒
     */
    public static void setTaskDuration(int taskDuration) {
        PageTriggerManager.taskDuration = taskDuration;
    }
    public static void DEBUG(){
        RposedHelpers.findAndHookMethod(View.class,
                "onTouchEvent", MotionEvent.class, new RC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        CLog.e("onTouchEvent -> toString "+param.args[0].toString());
                    }
                });
    }
    public static void setDisable(boolean disable) {
        PageTriggerManager.disable = disable;
    }

    public static Handler getMainLooperHandler() {
        return mainLooperHandler;
    }

    public static void addDialogHandler(DialogFocusHandler dialogFocusHandler) {
        if (dialogFocusHandler == null) {
            return;
        }
        dialogFocusHandlers.add(dialogFocusHandler);
    }

    public static void addHandler(String activityClassName, ActivityFocusHandler activityFocusHandler) {
        if (StringUtils.isBlank(activityClassName) || activityFocusHandler == null) {
            return;
        }
        activityFocusHandlerMap.put(activityClassName, activityFocusHandler);
    }

    public static void addHandler(String fragmentClassName, FragmentFocusHandler fragmentFocusHandler) {
        if (StringUtils.isBlank(fragmentClassName) || fragmentFocusHandler == null) {
            return;
        }
        fragmentFocusHandlerHashMap.put(fragmentClassName, fragmentFocusHandler);
    }

    public static Activity getTopActivity() {
        return topActivity;
    }

    private static Window getTopDialogWindow() {
        for (WeakReference<Window> windowWeakReference : dialogWindowsSets) {
            Window window = windowWeakReference.get();
            if (window == null) {
                dialogWindowsSets.remove(windowWeakReference);
                continue;
            }
            if (window.getDecorView().getVisibility() != View.VISIBLE) {
                Log.i(SuperAppium.TAG, "getTopDialogWindow ->getVisibility  continue ");
                continue;
            }
            if (!window.getDecorView().hasWindowFocus()) {
                Log.i(SuperAppium.TAG, "getTopDialogWindow ->hasWindowFocus  continue ");
                continue;
            }
            Log.i(SuperAppium.TAG, "get getTopDialogWindow: " + window.peekDecorView().hasWindowFocus());
            return window;
        }
        Log.i(SuperAppium.TAG, "get getTopDialogWindow == null ");
        return null;
    }

    private static View getTopPupWindowView() {
        for (WeakReference<PopupWindow> popupWindowWeakReference : popupWindowSets) {
            PopupWindow popupWindow = popupWindowWeakReference.get();
            if (popupWindow == null) {
                popupWindowSets.remove(popupWindowWeakReference);
                continue;
            }
            View mDecorView = (View) RposedHelpers.getObjectField(popupWindow, "mDecorView");
            if (mDecorView == null) {
                continue;
            }
            if (mDecorView.getVisibility() != View.VISIBLE) {
                continue;
            }
            return mDecorView;
        }
        return null;
    }


    public static View getTopRootView() {
        Activity topActivity = PageTriggerManager.getTopActivity();
        if (topActivity != null) {
            View rootView = topActivity.getWindow().getDecorView();
            if (rootView.getVisibility() == View.VISIBLE) {
                return rootView;
            }
            Log.w(SuperAppium.TAG, "target activity : " + topActivity + " not visible!!");
        }
        //先尝试从对话框内部获取
        Window dialogWindow = PageTriggerManager.getTopDialogWindow();
        if (dialogWindow != null) {
            //peekDecorView 获取最顶层view
            View rootView = dialogWindow.peekDecorView();
            if (rootView!=null&&rootView.getVisibility() == View.VISIBLE) {
                Log.w(SuperAppium.TAG, "getTopRootView dialogWindow == View.VISIBLE ");
                return rootView;
            }
        }
        //尝试从TopPop里面获取
        return getTopPupWindowView();

    }


    public static List<Object> getTopFragment() {
        List<Object> ret = new ArrayList<>();
        for (String theFragmentClassName : topFragmentMaps.keySet()) {
            Object topFragment = getTopFragment(theFragmentClassName);
            if (topFragment == null) {
                continue;
            }
            ret.add(topFragment);
        }
        return ret;
    }

    public static Object getTopFragment(String fragmentClassName) {
        Object fragmentObject = topFragmentMaps.get(fragmentClassName);
        if (fragmentObject == null) {
            return null;
        }
        boolean isVisible = (boolean) RposedHelpers.callMethod(fragmentObject, "isVisible");
        if (isVisible) {
            return fragmentObject;
        } else {
            topFragmentMaps.remove(fragmentClassName);
        }
        return null;
    }

    private static void enablePageMonitor(ClassLoader classLoader) {
        Log.e(SuperAppium.TAG, "start init PageTriggerManager hook onResume ");

        try {
            RposedHelpers.findAndHookMethod(Activity.class, "onResume", new RC_MethodHook() {
                @Override
                protected void afterHookedMethod(RC_MethodHook.MethodHookParam param) {

                    final Activity activity = (Activity) param.thisObject;
//                    for (WeakReference<LocalActivityManager> localActivityManagerWeakReference : localActivityManagers) {
//                        LocalActivityManager localActivityManager = localActivityManagerWeakReference.get();
//                        if (localActivityManager == null) {
//                            localActivityManagers.remove(localActivityManagerWeakReference);
//                            continue;
//                        }
//                        ArrayList arrayList = (ArrayList) RposedHelpers.getObjectField(localActivityManager, "mActivityArray");
//                        for (Object localActivityRecord : arrayList) {
//                            Activity localActivityObj = (Activity) RposedHelpers.getObjectField(localActivityRecord, "activity");
//                            if (activity.equals(localActivityObj)) {
//                                //这个activity 也有焦点，但是他是作为一个组件放到容器里面的，所以不应该被我们监听
//                                Log.i(SuperAppium.TAG, "localActivity :" + localActivityObj.getLocalClassName() + ",activity :" + activity.getLocalClassName());
//                                return;
//                            }
//                        }
//                    }
                    ThreadUtils.runOnMainThread(() -> topActivity = activity);
                    trigger();
                }
            });
        } catch (Throwable exception) {
            Log.e(SuperAppium.TAG, "SuperAppium Activity->onResume: error " + exception.getMessage());
            System.exit(0);
        }

        try {

            RC_MethodHook rc_methodHook = new RC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Log.i(SuperAppium.TAG, "onFragment resume: " + param.thisObject.getClass().getName());
                    topFragmentMaps.put(param.thisObject.getClass().getName(), param.thisObject);
                    triggerFragment();
                }
            };

            RposedHelpers.findAndHookMethod(Fragment.class, "onResume", rc_methodHook);

            Class<?> fragmentV4Class = classLoader.loadClass("android.support.v4.app.Fragment");
            if (fragmentV4Class != null) {
                RposedHelpers.findAndHookMethod(fragmentV4Class, "onResume", rc_methodHook);
            }

            Class<?> fragmentXClass = classLoader.loadClass("androidx.fragment.app.Fragment");
            if (fragmentXClass != null) {
                RposedHelpers.findAndHookMethod(fragmentXClass, "onResume", rc_methodHook);
            }
        } catch (ClassNotFoundException e) {
            //ignore
        }

        //android.app.LocalActivityManager.LocalActivityManager
//        RposedHelpers.findAndHookConstructor(LocalActivityManager.class, Activity.class, boolean.class, new RC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                localActivityManagers.add(new WeakReference<>((LocalActivityManager) param.thisObject));
//            }
//        });


        //弹窗不被 activity管理
        RposedBridge.hookAllConstructors(Dialog.class, new RC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Window mWindow = (Window)
                        RposedHelpers.getObjectField(param.thisObject, "mWindow");
                if (mWindow == null) {
                    Log.w(SuperAppium.TAG, "can not get windows object for dialog: " + param.thisObject.getClass().getName());
                    return;
                }
                Log.i(SuperAppium.TAG, "create dialog: " + param.thisObject.getClass().getName());
                dialogWindowsSets.add(new WeakReference<>(mWindow));
            }
        });

        //弹窗show时回调处理
        RposedHelpers.findAndHookMethod(Dialog.class, "show", new RC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Dialog dialog = (Dialog) param.thisObject;
                for (DialogFocusHandler dialogFocusHandler : dialogFocusHandlers) {
                    triggerDialogActive(dialog, dialogFocusHandler, 0);
                }
            }
        });

        //popupWindow不被activity管理
        RposedHelpers.findAndHookConstructor(PopupWindow.class, View.class, int.class, int.class, boolean.class, new RC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Log.i(SuperAppium.TAG, "create PopupWindow: " + param.thisObject.getClass().getName());
                popupWindowSets.add(new WeakReference<>((PopupWindow) param.thisObject));
            }
        });


        Log.i(SuperAppium.TAG, ">>>>>>>>>>>>>>>>>>>>>>SuperAppium init sucess!<<<<<<<<<<<<<<<<<<<<<<<<<<<<  ");
    }

    public static void trigger(int delay) {
        if (delay <= taskDuration) {
            trigger();
            return;
        }
        mainLooperHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                trigger();
            }
        }, delay);
    }


    /**
     * fix 此方法一定要在主进程去执行
     */
    public static void trigger() {
        ThreadUtils.runOnMainThread(() -> {
            final Activity activity = getTopActivity();
            if (activity == null) {
                Log.e(SuperAppium.TAG, "no top activity found "
                        + AppUtils.getProcessName());
                return;
            }

            final ActivityFocusHandler iActivityHandler = activityFocusHandlerMap.get(activity.getClass().getName());
            if (iActivityHandler != null) {
                Log.i(SuperAppium.TAG, "activity is register:" + activity.getClass().getName());
                if (hasPendingActivityTask) {
                    Log.e(SuperAppium.TAG, activity.getClass().getName() + " : hasPendingActivityTask==true");
                    return;
                }
                triggerActivityActive(activity, iActivityHandler, 0);
            }
        });
    }

    public static void triggerFragment() {
        for (String theFragmentClassName : topFragmentMaps.keySet()) {
            Object topFragment = getTopFragment(theFragmentClassName);
            if (topFragment == null) {
                continue;
            }
            FragmentFocusHandler fragmentFocusHandler = fragmentFocusHandlerHashMap.get(theFragmentClassName);
            if (fragmentFocusHandler == null) {
                continue;
            }
            triggerFragmentActive(topActivity, topFragment, fragmentFocusHandler, 0);
        }
    }

    private static void triggerDialogActive(final Dialog dialog, final DialogFocusHandler dialogFocusHandler, final int triggerCount) {
        if (disable) {
            Log.i(SuperAppium.TAG, "Page Trigger manager disabled");
            return;
        }
        Log.i(SuperAppium.TAG, "trigger dialog:" + dialog.getClass().getName() + ",dialogFocusHandler:" + dialogFocusHandler.getClass().getName());
        mainLooperHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    Window mWindow = (Window) RposedHelpers.getObjectField(dialog, "mWindow");
                    if (mWindow == null) {
                        Log.w(SuperAppium.TAG, "dialog showed but donot found the root window: " + dialog.getClass().getName());
                        return;
                    }
                    if (dialogFocusHandler.handleDialog(dialog, new ViewImage(mWindow.getDecorView()))) {
                        return;
                    }
                } catch (Throwable throwable) {
                    Log.e(SuperAppium.TAG, "error to handle dialog: " + dialog.getClass().getName(), throwable);
                }
                if (triggerCount > 10) {
                    Log.w(SuperAppium.TAG, "the dialog event trigger failed too many times: " + dialogFocusHandler.getClass());
                    return;
                }
                triggerDialogActive(dialog, dialogFocusHandler, triggerCount + 1);
            }
        });
    }


    private static void triggerFragmentActive(final Activity activity, final Object fragment, final FragmentFocusHandler fragmentFocusHandler, final int triggerCount) {
        if (disable) {
            Log.i(SuperAppium.TAG, "Page Trigger manager disabled");
            return;
        }
        Log.i(SuperAppium.TAG, "trigger fragment:" + fragment.getClass().getName() + ",fragmentFocusHandler:" + fragmentFocusHandler.getClass().getName());
        mainLooperHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!activity.hasWindowFocus()) {
                    return;
                }
                try {
                    if (fragmentFocusHandler.handleFragmentPage(fragment, activity, new ViewImage((View) RposedHelpers.callMethod(fragment, "getView")))) {
                        return;
                    }
                } catch (Throwable throwable) {
                    Log.e(SuperAppium.TAG, "error to handle fragment: " + fragment.getClass().getName(), throwable);
                }
                if (triggerCount > 10) {
                    Log.w(SuperAppium.TAG, "the fragment event trigger failed too many times: " + fragmentFocusHandler.getClass());
                    fragmentFocusHandler.onRetryFailed(fragment, activity, new ViewImage((View) RposedHelpers.callMethod(fragment, "getView")));
                    return;
                }
                triggerFragmentActive(activity, fragment, fragmentFocusHandler, triggerCount + 1);
            }
        }, taskDuration);
    }

    /**
     * @param activity
     * @param activityFocusHandler
     * @param triggerCount
     */
    private static void triggerActivityActive(final Activity activity, final ActivityFocusHandler activityFocusHandler, final int triggerCount) {
        if (disable) {
            Log.i(SuperAppium.TAG, "Page Trigger manager disabled");
            return;
        }
        hasPendingActivityTask = true;
        Log.d(SuperAppium.TAG, "trigger taskDuration:" + taskDuration);
        mainLooperHandler.postDelayed(() -> {
//                if (!activity.hasWindowFocus()) {
//                    return;
//                }
            try {
                Log.i(SuperAppium.TAG, String.format("triggerActivityActive activity: %s for ActivityFocusHandler: %s %d times", activity.getClass().getName(), activityFocusHandler.getClass().getName(), triggerCount));
                hasPendingActivityTask = false;
                if (activityFocusHandler.handleActivity(activity, new ViewImage(activity.getWindow().getDecorView()))) {
                    return;
                }
                if (triggerCount > 10) {
                    activityFocusHandler.onRetryFailed(activity, new ViewImage(activity.getWindow().getDecorView()));
                    Log.w(SuperAppium.TAG, "the activity event trigger failed too many times: " + activityFocusHandler.getClass());
                    return;
                }
                triggerActivityActive(activity, activityFocusHandler, triggerCount + 1);
            } catch (Throwable throwable) {
                Log.e(SuperAppium.TAG, "error to handle activity:" + activity.getClass().getName(), throwable);
            }
        }, taskDuration);
    }

}

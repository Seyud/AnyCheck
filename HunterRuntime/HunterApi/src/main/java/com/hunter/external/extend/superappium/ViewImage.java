package com.hunter.external.extend.superappium;

import static android.view.MotionEvent.TOOL_TYPE_FINGER;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.EditText;

import com.hunter.api.rposed.RposedHelpers;
import com.hunter.external.extend.superappium.traversor.Collector;
import com.hunter.external.extend.superappium.traversor.Evaluator;
import com.hunter.external.extend.superappium.traversor.SuperAppiumDumper;
import com.hunter.external.extend.superappium.xmodel.LazyValueGetter;
import com.hunter.external.extend.superappium.xmodel.ValueGetters;
import com.hunter.external.extend.superappium.xpath.XpathParser;
import com.hunter.external.extend.superappium.xpath.model.XNode;
import com.hunter.external.extend.superappium.xpath.model.XNodes;
import com.hunter.external.utils.CLog;
import com.hunter.external.utils.ThreadUtils;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


public class ViewImage {
    private static Handler  handler = new Handler(Looper.getMainLooper());
    private final View originView;
    private final Map<String, LazyValueGetter> attributes;
    private ViewImage parent = null;
    private LazyValueGetter<String> type;
    private LazyValueGetter<String> text;
    private int indexOfParent = -1;
    private ViewImages allElementsCache = null;

    public ViewImage(View originView) {
        this.originView = originView;
        attributes = ValueGetters.valueGetters(this);
        type = attrName(SuperAppium.baseClassName);
        text = attrName(SuperAppium.text);
    }

    /**
     * 点击的回调,延迟多少秒点击当前View
     * 可设置延迟时间
     */
    public static interface ClickCallBack{
        public void callback(boolean isSucess);
    }

    public String getType() {
        return type.get();
    }

    public String getText() {
        return text.get();
    }

    @SuppressWarnings("unchecked")
    private <T> LazyValueGetter<T> attrName(String attrName) {
        return attributes.get(attrName);
    }

    public Collection<String> attributeKeys() {
        return attributes.keySet();
    }

    /**
     * // TODO: 2021/6/21 出现报错，这块需要优化下
     * java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference
     * at com.hunter.external.extend.superappium.xmodel.ValueGetters.valueGetters(ValueGetters.java:64)
     * at com.hunter.external.extend.superappium.ViewImage.<init>(ViewImage.java:49)
     * at com.hunter.external.extend.superappium.ViewImage.childAt(ViewImage.java:113)
     * at com.hunter.external.extend.superappium.ViewImage.nextSibling(ViewImage.java:171)
     * at com.hunter.external.extend.superappium.traversor.NodeTraversor.traverse(NodeTraversor.java:34)
     * at com.hunter.external.extend.superappium.traversor.Collector.collect(Collector.java:22)
     * at com.hunter.external.extend.superappium.xpath.function.select.TagSelectFunction.call(TagSelectFunction.java:29)
     * at com.hunter.external.extend.superappium.xpath.model.XpathEvaluator$ChainEvaluator.handleNode(XpathEvaluator.java:82)
     * at com.hunter.external.extend.superappium.xpath.model.XpathEvaluator$ChainEvaluator.evaluate(XpathEvaluator.java:113)
     * at com.hunter.external.extend.superappium.xpath.model.XpathEvaluator.evaluateToElement(XpathEvaluator.java:35)
     * at com.hunter.external.extend.superappium.ViewImage.xpath(ViewImage.java:225)
     * at com.crack.meituan.page.hotelSearch.HotelSearchResultActivityHandler.lambda$handleActivity$0$HotelSearchResultActivityHandler(HotelSearchResultActivityHandler.java:88)
     * at com.crack.meituan.page.hotelSearch.-$$Lambda$HotelSearchResultActivityHandler$MAMbv1424pPcJqQJRma4fguLIBk.run(Unknown Source:6)
     * at java.lang.Thread.run(Thread.java:784)
     *
     * @param key
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        LazyValueGetter valueGetter = attributes.get(key);
        if (valueGetter == null) {
            return null;
        }
        return (T) valueGetter.get();
    }


    public View getOriginView() {
        return originView;
    }

    private Integer theChildCount = null;

    public int childCount() {
        if (theChildCount != null) {
            return theChildCount;
        }
        if (!(originView instanceof ViewGroup)) {
            return 0;
        }
        ViewGroup viewGroup = (ViewGroup) originView;
        theChildCount = viewGroup.getChildCount();
        return theChildCount;
    }

    private ViewImage[] children;

    public ViewImage childAt(int index) {
        if (childCount() < 0) {
            throw new IllegalStateException("can not parse child node for none ViewGroup object!!");
        }
        if (children == null) {
            children = new ViewImage[childCount()];
        }
        ViewImage viewImage = children[index];
        if (viewImage != null) {
            return viewImage;
        }
        ViewGroup viewGroup = (ViewGroup) originView;
        viewImage = new ViewImage(viewGroup.getChildAt(index));
        viewImage.parent = this;
        viewImage.indexOfParent = index;
        children[index] = viewImage;
        return viewImage;
    }

    public Integer index() {
        return indexOfParent;
    }


    public List<ViewImage> parents() {
        List<ViewImage> ret = new ArrayList<>();
        ViewImage parent = this.parent;
        while (parent != null) {
            ret.add(parent);
            parent = parent.parent;
        }
        return ret;
    }

    public List<ViewImage> children() {
        if (childCount() <= 0) {
            return new ArrayList<>();
        }
        List<ViewImage> ret = new ArrayList<>(childCount());
        for (int i = 0; i < childCount(); i++) {
            ret.add(childAt(i));
        }
        return ret;
    }

    public ViewImages getAllElements() {
        if (allElementsCache == null) {
            allElementsCache = Collector.collect(new Evaluator.AllElements(), this);
        }
        return allElementsCache;
    }

    public ViewImage parentNode() {
        return parent;
    }

    public ViewImage parentNode(int n) {
        if (n == 1) {
            return parentNode();
        }
        return parentNode().parentNode(n - 1);
    }

    public ViewImage nextSibling() {
        if (parent == null) {
            //root
            return null;
        }
        int nextSiblingIndex = indexOfParent + 1;
        if (parent.childCount() > nextSiblingIndex) {
            return parent.childAt(nextSiblingIndex);
        }
        return null;
    }

    public ViewImage previousSibling() {
        if (parent == null) {
            //root
            return null;
        }
        int nextSiblingIndex = indexOfParent - 1;
        if (nextSiblingIndex < 0) {
            return null;
        }
        return parent.childAt(nextSiblingIndex);
    }

    public ViewImages siblings() {
        if (parent == null) {
            return new ViewImages();
        }
        int parentChildren = parent.childCount();
        ViewImages viewImages = new ViewImages(parentChildren - 1);
        for (int i = 0; i < parentChildren; i++) {
            ViewImage viewImage = parent.childAt(i);
            if (viewImage == this) {
                continue;
            }
            viewImages.add(viewImage);
        }
        return viewImages;
    }

    public String attributes() {
        JSONObject jsonObject = new JSONObject();
        for (String key : attributeKeys()) {
            try {
                jsonObject.put(key, (Object) attribute(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonObject.toString();
    }

    public ViewImage rootViewImage() {
        ViewImage parentViewImage = parentNode();
        if (parentViewImage == null) {
            return this;
        }
        return parentViewImage.rootViewImage();
    }

    public ViewImages xpath(String xpath) {
        return XpathParser.compileNoError(xpath).evaluateToElement(new XNodes(XNode.e(this)));
    }

    public String xpath2String(String xpath) {
        return XpathParser.compileNoError(xpath).evaluateToSingleString(new XNodes(XNode.e(this)));
    }

    public ViewImage xpath2One(String xpath) {
        ViewImages viewImages = xpath(xpath);
        if (viewImages.size() == 0) {
            //尝试从对话框和TopView,方便分析
            View topView = PageTriggerManager.getTopRootView();
            if(topView!=null) {
                CLog.i("xpath2One getTopRootView!=null  "+xpath);
                ViewImage topRootView = new ViewImage(topView);
                viewImages = topRootView.xpath(xpath);
            }else {
                CLog.i("xpath2One PageTriggerManager.getTopRootView() == null "+xpath);
                return null;
            }
            if(viewImages==null||viewImages.size() == 0){
                return null;
            }
        }
        CLog.i("xpath2One size ->  "+viewImages.size());
        return viewImages.get(0);
    }

    private boolean clickAdapterView(AdapterView parent, View mView) {
        final int position = parent.getPositionForView(mView);
        final long itemId = parent.getAdapter() != null
                ? parent.getAdapter().getItemId(position)
                : 0;
        if (position != AdapterView.INVALID_POSITION) {
            return parent.performItemClick(mView, position, itemId);
        }
        return false;
    }

    public boolean clickByXpath(String xpathExpression) {
        ViewImages viewImages = xpath(xpathExpression);
        if (viewImages.size() == 0) {
            return false;
        }
        return viewImages.get(0).click();
    }

    public boolean typeByXpath(String xpathExpression, String content) {
        ViewImages viewImages = xpath(xpathExpression);
        if (viewImages.size() == 0) {
            return false;
        }
        View originView = viewImages.get(0).getOriginView();
        if (!(originView instanceof EditText)) {
            return false;
        }
        EditText editText = (EditText) originView;
        editText.getText().clear();
        editText.setText(content);
        return true;
    }


    public void click(ClickCallBack callBack,long delay){
        ThreadUtils.runOnMainThread(() -> callBack.callback(click()),delay);
    }

    public boolean click() {
        if(clickV2()){
            CLog.i("click v2 success !");
            return true;
        }else {
            //尝试调用系统默认点击方法,这个地方直接调用可能出问题
            //先尝使用V2方式去点击
            if (originView.isClickable()) {
                if (originView.performClick()) {
                    return true;
                }
            }
            //如果是AdapterView 尝试对AdapterView进行事件分发
            ViewImage parentViewImage = parentNode();
            if (parentViewImage != null) {
                View parentOriginView = parentViewImage.getOriginView();
                if (parentOriginView instanceof AdapterView) {
                    if (!originView.performClick()) {
                        if (clickAdapterView((AdapterView) parentOriginView, originView)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @SuppressLint("NewApi")
    public boolean clickByView(View rootView,final float x, final float y){
        if(rootView==null){
            return false;
        }
        int[] loca = new int[2];
        rootView.getLocationOnScreen(loca);

        final float locationOnRootViewX = x - loca[0];
        final float locationOnRootViewY = y - loca[1];

        if (locationOnRootViewX < 0 || locationOnRootViewY < 0) {
            //点击到屏幕外面了
            return false;
        }
        if (locationOnRootViewX > rootView.getWidth() || locationOnRootViewY > rootView.getHeight()) {
            return false;
        }
        long downTime = SystemClock.uptimeMillis();

        if (!dispatchInputEvent(genMotionEvent(downTime,MotionEvent.ACTION_DOWN,
                new float[]{locationOnRootViewX, locationOnRootViewY}))) {
            return false;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dispatchInputEvent(genMotionEvent(downTime,MotionEvent.ACTION_UP, new float[]{
                        locationOnRootViewX, locationOnRootViewY}));
            }
        }, ThreadLocalRandom.current().nextInt(25) + 10);
        return true;
    }

    @SuppressLint("NewApi")
    public boolean clickbyActivity(Activity activity, final float x, final float y){
        View rootView = activity.getWindow().getDecorView();
       return clickByView(rootView,x,y);
    }


    @SuppressLint("NewApi")
    public boolean clickByPoint(final float x, final float y) {
        View rootView = rootViewImage().getOriginView();
        return clickByView(rootView,x,y);
    }


    public boolean dispatchInputEvent(InputEvent inputEvent) {
        CLog.i("dispatchInputEvent "+inputEvent.toString());
        View rootView = rootViewImage().getOriginView();
        final Object mViewRootImpl = RposedHelpers.callMethod(rootView, "getViewRootImpl");
        if (mViewRootImpl == null) {
            CLog.i("dispatchInputEvent error "+inputEvent);
            return false;
        }
        CLog.i("dispatchInputEvent success! "+inputEvent);
        RposedHelpers.callMethod(mViewRootImpl, "dispatchInputEvent", inputEvent);
        return true;
    }

    @SuppressLint("NewApi")
    private boolean clickV2() {
        float[] floats = measureClickPoint();
        return clickByPoint(floats[0], floats[1]);
    }

    private float[] measureClickPoint() {
        int[] locs = new int[2];
        originView.getLocationOnScreen(locs);
        float x = locs[0] + originView.getWidth() / 4.0f;
        float y = locs[1] + originView.getHeight() / 4.0f;

        float[] ret = new float[2];
        ret[0] = x;
        ret[1] = y;
        return ret;
    }

    public void swipe(int fromX, int fromY, int toX, int toY) {
        SwipeUtils.simulateScroll(this, fromX, fromY, toX, toY);
    }

    @SuppressLint("NewApi")
    public void swipeDown(int height) {
        int[] locs = new int[2];
        originView.getLocationOnScreen(locs);

        int viewWidth = originView.getWidth();
        int viewHeight = originView.getHeight();

        int fromX = (int) (locs[0] + viewWidth * (ThreadLocalRandom.current().nextDouble(0.4) - 0.2));
        int toX = (int) (fromX + viewWidth * (ThreadLocalRandom.current().nextDouble(0.1)));


        int fromY = (int) (locs[1] + viewHeight * ThreadLocalRandom.current().nextDouble(0.1));
        int toY = fromY + height;
        SwipeUtils.simulateScroll(this, fromX, fromY, toX, toY);

    }

    public static MotionEvent genMotionEvent(long downTime ,int action, float[] point) {
        CLog.i("create MotionEvent action "+action);

        long eventTime = SystemClock.uptimeMillis();

        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.x = point[0];
        pointerCoords.y = point[1];
        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.id = 0;
        pointerProperties.toolType = TOOL_TYPE_FINGER;
        MotionEvent.PointerProperties[] pointerPropertiesArray = new MotionEvent.PointerProperties[]{pointerProperties};
        MotionEvent.PointerCoords[] pointerCoordsArray = new MotionEvent.PointerCoords[]{pointerCoords};
        return MotionEvent.obtain(
                downTime, eventTime, action,
                1, pointerPropertiesArray, pointerCoordsArray,
                0, 0, 0, 0, 8, 0, 4098, 0
        );
    }

    public static MotionEvent genMotionEvent(long downTime ,int action, float[] point,int devicesId,int flags) {
        CLog.i("create MotionEvent action "+action);

        long eventTime = SystemClock.uptimeMillis();

        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.x = point[0];
        pointerCoords.y = point[1];
        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.id = 0;
        pointerProperties.toolType = TOOL_TYPE_FINGER;
        MotionEvent.PointerProperties[] pointerPropertiesArray = new MotionEvent.PointerProperties[]{pointerProperties};
        MotionEvent.PointerCoords[] pointerCoordsArray = new MotionEvent.PointerCoords[]{pointerCoords};
        return MotionEvent.obtain(
                downTime, eventTime, action,
                1, pointerPropertiesArray, pointerCoordsArray,
                0, 0, 0, 0, devicesId, 0, 4098, flags
        );
    }

    public WebView findWebViewIfExist() {
        ViewImages webViews = Collector.collect(new Evaluator() {

            @Override
            public boolean matches(ViewImage root, ViewImage element) {
                return element.getOriginView() instanceof WebView;
            }

            @Override
            public boolean onlyOne() {
                return true;
            }
        }, this);
        if (webViews.size() == 0) {
            return null;
        }
        return (WebView) webViews.get(0).getOriginView();
    }


    @Override
    public String toString() {
        return SuperAppiumDumper.dumpToJson(this);
    }
}

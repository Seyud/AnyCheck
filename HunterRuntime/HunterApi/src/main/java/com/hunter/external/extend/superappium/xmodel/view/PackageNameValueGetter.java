package com.hunter.external.extend.superappium.xmodel.view;


import android.content.Context;
import android.view.View;

import com.hunter.api.rposed.RposedHelpers;
import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;


public class PackageNameValueGetter implements ValueGetter<String> {

    @Override
    public String get(ViewImage viewImage) {
        View originView = viewImage.getOriginView();
        Context context = (Context) RposedHelpers.getObjectField(originView, "mContext");
        return context.getPackageName();
    }

    @Override
    public boolean support(Class type) {
        return true;
    }

    @Override
    public String attr() {
        return SuperAppium.packageName;
    }
}

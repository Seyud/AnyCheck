package com.hunter.external.extend.superappium.xmodel.basic;

import android.widget.ImageView;

import com.hunter.api.rposed.RposedHelpers;
import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;


public class ImageUriGetter implements ValueGetter {
    @Override
    public Object get(ViewImage viewImage) {
        return RposedHelpers.getObjectField(viewImage.getOriginView(), "mUri");
    }

    @Override
    public String attr() {
        return SuperAppium.mUri;
    }

    @Override
    public boolean support(Class type) {
        return ImageView.class.isAssignableFrom(type);
    }
}

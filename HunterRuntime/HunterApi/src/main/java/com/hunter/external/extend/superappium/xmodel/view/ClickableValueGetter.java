package com.hunter.external.extend.superappium.xmodel.view;

import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;

public class ClickableValueGetter implements ValueGetter<Boolean> {

    @Override
    public Boolean get(ViewImage viewImage) {
        return viewImage.getOriginView().isClickable();
    }

    @Override
    public boolean support(Class type) {
        return true;
    }

    @Override
    public String attr() {
        return SuperAppium.clickable;
    }
}

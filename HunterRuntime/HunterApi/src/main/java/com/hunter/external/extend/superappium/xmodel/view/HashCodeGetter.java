package com.hunter.external.extend.superappium.xmodel.view;

import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;

public class HashCodeGetter implements ValueGetter<String> {
    @Override
    public String get(ViewImage viewImage) {
        return String.valueOf(viewImage.getOriginView().hashCode());
    }

    @Override
    public boolean support(Class type) {
        return true;
    }

    @Override
    public String attr() {
        return SuperAppium.hash;
    }
}

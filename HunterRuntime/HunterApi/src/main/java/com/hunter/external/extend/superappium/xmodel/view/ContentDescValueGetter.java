package com.hunter.external.extend.superappium.xmodel.view;

import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;

public class ContentDescValueGetter implements ValueGetter<String> {

    @Override
    public String get(ViewImage viewImage) {
        CharSequence contentDescription = viewImage.getOriginView().getContentDescription();
        if (contentDescription == null) {
            return null;
        }
        return contentDescription.toString();
    }

    @Override
    public boolean support(Class type) {
        return true;
    }

    @Override
    public String attr() {
        return SuperAppium.contentDescription;
    }
}

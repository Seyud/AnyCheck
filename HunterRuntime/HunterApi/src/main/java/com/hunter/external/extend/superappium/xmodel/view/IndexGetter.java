package com.hunter.external.extend.superappium.xmodel.view;

import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;

public class IndexGetter implements ValueGetter<Integer> {
    @Override
    public Integer get(ViewImage viewImage) {
        return viewImage.index();
    }

    @Override
    public boolean support(Class type) {
        return true;
    }

    @Override
    public String attr() {
        return SuperAppium.index;
    }
}

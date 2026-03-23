package com.hunter.external.extend.superappium.xpath.function.axis;


import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.function.NameAware;

import java.util.List;

public abstract interface AxisFunction extends NameAware {
    public abstract List<ViewImage> call(ViewImage paramViewImage, List<String> paramList);
}

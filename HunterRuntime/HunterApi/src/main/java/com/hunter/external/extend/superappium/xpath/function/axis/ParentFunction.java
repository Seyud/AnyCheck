package com.hunter.external.extend.superappium.xpath.function.axis;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.ViewImages;

import java.util.List;


public class ParentFunction implements AxisFunction {
    @Override
    public ViewImages call(ViewImage e, List<String> args) {
        return new ViewImages(e.parentNode());
    }

    @Override
    public String getName() {
        return "parent";
    }
}

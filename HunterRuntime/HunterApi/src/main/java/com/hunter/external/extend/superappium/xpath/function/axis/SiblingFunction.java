package com.hunter.external.extend.superappium.xpath.function.axis;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.ViewImages;

import java.util.List;

public class SiblingFunction implements AxisFunction {
    @Override
    public ViewImages call(ViewImage e, List<String> args) {
        return new ViewImages(e.nextSibling());
    }

    @Override
    public String getName() {
        return "sibling";
    }
}


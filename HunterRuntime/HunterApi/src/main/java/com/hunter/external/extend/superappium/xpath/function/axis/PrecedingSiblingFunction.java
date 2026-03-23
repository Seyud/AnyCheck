package com.hunter.external.extend.superappium.xpath.function.axis;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.ViewImages;

import java.util.LinkedList;
import java.util.List;




public class PrecedingSiblingFunction implements AxisFunction {
    @Override
    public ViewImages call(ViewImage e, List<String> args) {
        ViewImage tmp = e.previousSibling();
        LinkedList<ViewImage> tempList = new LinkedList<>();
        while (tmp != null) {
            tempList.addFirst(tmp);
            tmp = tmp.previousSibling();
        }
        return new ViewImages(tempList);
    }

    @Override
    public String getName() {
        return "preceding-sibling";
    }
}

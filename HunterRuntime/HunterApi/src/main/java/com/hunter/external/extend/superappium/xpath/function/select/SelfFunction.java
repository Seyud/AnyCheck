package com.hunter.external.extend.superappium.xpath.function.select;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.ViewImages;
import com.hunter.external.extend.superappium.xpath.model.XNode;
import com.hunter.external.extend.superappium.xpath.model.XNodes;
import com.hunter.external.extend.superappium.xpath.model.XpathNode;

import java.util.List;


public class SelfFunction implements SelectFunction {
    @Override
    public XNodes call(XpathNode.ScopeEm scopeEm, ViewImages elements, List<String> args) {
        XNodes xNodes = new XNodes();
        for (ViewImage viewImage : elements) {
            xNodes.add(XNode.e(viewImage));
        }
        return xNodes;
    }

    @Override
    public String getName() {
        return "self";
    }
}

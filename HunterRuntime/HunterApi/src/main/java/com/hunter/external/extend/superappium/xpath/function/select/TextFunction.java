package com.hunter.external.extend.superappium.xpath.function.select;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.ViewImages;
import com.hunter.external.extend.superappium.xpath.model.XNode;
import com.hunter.external.extend.superappium.xpath.model.XNodes;
import com.hunter.external.extend.superappium.xpath.model.XpathNode;

import java.util.List;

public class TextFunction implements SelectFunction {
    /**
     * 只获取节点自身的子文本
     */
    @Override
    public XNodes call(XpathNode.ScopeEm scopeEm, ViewImages elements, List<String> args) {
        XNodes res = new XNodes();
        if (elements != null && elements.size() > 0) {
            for (ViewImage e : elements) {
                res.add(XNode.t(e.getText()));
            }
        }
        return res;
    }

    @Override
    public String getName() {
        return "text";
    }
}


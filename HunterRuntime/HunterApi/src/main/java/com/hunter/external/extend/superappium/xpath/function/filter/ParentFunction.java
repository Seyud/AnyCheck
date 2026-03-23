package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.XpathUtil;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public class ParentFunction implements FilterFunction {
    @Override
    public Object call(ViewImage element, List<SyntaxNode> params) {
        int index = 1;
        Integer integer = XpathUtil.firstParamToInt(params, element, getName());
        if (integer != null) {
            index = integer;
        }
        for (int i = 0; i < index; i++) {
            element = element.parentNode();
        }
        return element;
    }

    @Override
    public String getName() {
        return "parent";
    }
}

package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public class LastFunction implements FilterFunction {
    public Object call(ViewImage element, List<SyntaxNode> params) {
        return Boolean.valueOf(element.index().intValue() == element.parentNode().childCount() - 1);
    }

    public String getName() {
        return "last";
    }
}

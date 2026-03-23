package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public class ContainsFunction implements FilterFunction {
    public Object call(ViewImage element, List<SyntaxNode> params) {
        return Boolean.valueOf(((SyntaxNode) params.get(0)).calc(element).toString().contains(((SyntaxNode) params.get(1)).calc(element).toString()));
    }

    public String getName() {
        return "contains";
    }
}

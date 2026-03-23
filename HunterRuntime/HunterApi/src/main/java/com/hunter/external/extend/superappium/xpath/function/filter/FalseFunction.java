package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public class FalseFunction implements FilterFunction {
    public Object call(ViewImage element, List<SyntaxNode> params) {
        return Boolean.valueOf(false);
    }

    public String getName() {
        return "false";
    }
}


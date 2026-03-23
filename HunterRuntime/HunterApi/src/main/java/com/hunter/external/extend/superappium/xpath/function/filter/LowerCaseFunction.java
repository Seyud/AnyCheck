package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public class LowerCaseFunction extends AbstractStringFunction {
    public Object call(ViewImage element, List<SyntaxNode> params) {
        return firstParamToString(element, params).toLowerCase();
    }

    public String getName() {
        return "lower-case";
    }
}

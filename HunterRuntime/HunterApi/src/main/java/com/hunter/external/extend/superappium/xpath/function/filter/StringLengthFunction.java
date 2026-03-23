package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public class StringLengthFunction extends AbstractStringFunction {
    @Override
    public Object call(ViewImage element, List<SyntaxNode> params) {
        return firstParamToString(element, params).length();
    }

    @Override
    public String getName() {
        return "string-length";
    }
}

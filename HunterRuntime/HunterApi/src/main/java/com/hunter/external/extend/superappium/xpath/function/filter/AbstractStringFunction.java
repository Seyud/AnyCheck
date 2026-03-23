package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.exception.EvaluateException;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;

public abstract class AbstractStringFunction implements FilterFunction {
    protected String firstParamToString(ViewImage element, List<SyntaxNode> params) {
        Object string = ((SyntaxNode) params.get(0)).calc(element);
        if (!(string instanceof String)) {
            throw new EvaluateException(getName() + " first parameter is not a string :" + string);
        }
        return string.toString();
    }
}

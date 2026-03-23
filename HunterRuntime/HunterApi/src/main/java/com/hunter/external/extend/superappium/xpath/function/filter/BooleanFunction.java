package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.apache.commons.lang3.BooleanUtils;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.List;



public class BooleanFunction
        implements FilterFunction {
    public Object call(ViewImage element, List<SyntaxNode> params) {
        if (params.size() == 0) {
            return Boolean.valueOf(false);
        }
        Object calc = ((SyntaxNode) params.get(0)).calc(element);
        if (calc == null) {
            return Boolean.valueOf(false);
        }
        if ((calc instanceof Boolean)) {
            return calc;
        }
        if ((calc instanceof String)) {
            return Boolean.valueOf(BooleanUtils.toBoolean(calc.toString()));
        }
        if ((calc instanceof Integer)) {
            return Boolean.valueOf(((Integer) calc).intValue() != 0);
        }
        if ((calc instanceof Number)) {
            return Boolean.valueOf(((Number) calc).doubleValue() > 0.0D);
        }
        return Boolean.valueOf(false);
    }

    public String getName() {
        return "boolean";
    }
}

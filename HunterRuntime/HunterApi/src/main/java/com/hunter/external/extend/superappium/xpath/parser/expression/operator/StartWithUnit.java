package com.hunter.external.extend.superappium.xpath.parser.expression.operator;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.XpathUtil;
import com.hunter.external.extend.superappium.xpath.parser.expression.node.AlgorithmUnit;


/**
 * Created by virjar on 17/6/10.
 */
@OpKey(value = "^=", priority = 10)
public class StartWithUnit extends AlgorithmUnit {
    @Override
    public Object calc(ViewImage element) {
        Object leftValue = left.calc(element);
        Object rightValue = right.calc(element);
        if (leftValue == null || rightValue == null) {
            return Boolean.FALSE;
        }
        return XpathUtil.toPlainString(leftValue).startsWith(XpathUtil.toPlainString(rightValue));
    }
}

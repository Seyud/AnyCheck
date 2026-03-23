package com.hunter.external.extend.superappium.xpath.function.filter;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import java.util.Iterator;
import java.util.List;

public class ConcatFunction implements FilterFunction {
    public Object call(ViewImage element, List<SyntaxNode> params) {
        if (params.size() == 0) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        Iterator<SyntaxNode> iterator = params.iterator();
        stringBuilder.append(((SyntaxNode) iterator.next()).calc(element));
        while (iterator.hasNext()) {
            stringBuilder.append(" ").append(((SyntaxNode) iterator.next()).calc(element));
        }
        return stringBuilder.toString();
    }

    public String getName() {
        return "concat";
    }
}

package com.hunter.external.extend.superappium.xpath.model;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.function.FunctionEnv;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;

import com.hunter.external.miniguava.Lists;

public class Predicate {
    private SyntaxNode syntaxNode;
    private String predicateStr;

    public String getPredicateStr() {
        return predicateStr;
    }

    public Predicate(String predicateStr, SyntaxNode syntaxNode) {
        this.predicateStr = predicateStr;
        this.syntaxNode = syntaxNode;
    }

    boolean isValid(ViewImage element) {
        return (Boolean) FunctionEnv.getFilterFunction("sipSoupPredictJudge").call(element,
                Lists.newArrayList(syntaxNode));
    }
}

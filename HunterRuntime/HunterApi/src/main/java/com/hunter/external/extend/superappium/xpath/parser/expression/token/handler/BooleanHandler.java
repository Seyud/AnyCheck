package com.hunter.external.extend.superappium.xpath.parser.expression.token.handler;

import com.hunter.external.extend.superappium.xpath.exception.XpathSyntaxErrorException;
import com.hunter.external.extend.superappium.xpath.function.FunctionEnv;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;
import com.hunter.external.extend.superappium.xpath.parser.expression.node.FunctionNode;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.Token;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenHandler;

import com.hunter.external.miniguava.BooleanUtils;
import com.hunter.external.miniguava.Lists;

public class BooleanHandler implements TokenHandler {
    @Override
    public SyntaxNode parseToken(final String tokenStr) throws XpathSyntaxErrorException {
        return new FunctionNode(FunctionEnv.getFilterFunction(BooleanUtils.toBoolean(tokenStr) ? "true" : "false"),
                Lists.<SyntaxNode>newLinkedList());
    }

    @Override
    public String typeName() {
        return Token.BOOLEAN;
    }
}

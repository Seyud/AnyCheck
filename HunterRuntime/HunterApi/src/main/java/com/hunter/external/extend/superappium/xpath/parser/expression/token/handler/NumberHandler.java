package com.hunter.external.extend.superappium.xpath.parser.expression.token.handler;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.Token;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenHandler;

import com.hunter.external.miniguava.NumberUtils;


public class NumberHandler implements TokenHandler {
    @Override
    public SyntaxNode parseToken(final String tokenStr) {
        return new SyntaxNode() {
            @Override
            public Object calc(ViewImage element) {
                if (tokenStr.contains(".")) {
                    return NumberUtils.toDouble(tokenStr);
                } else {
                    return NumberUtils.toInt(tokenStr);
                }
            }
        };
    }

    @Override
    public String typeName() {
        return Token.NUMBER;
    }
}

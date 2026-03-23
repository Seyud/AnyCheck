package com.hunter.external.extend.superappium.xpath.parser.expression.token.handler;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.Token;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenHandler;

public class ConstantHandler implements TokenHandler {
    @Override
    public SyntaxNode parseToken(final String tokenStr) {
        return new SyntaxNode() {
            @Override
            public Object calc(ViewImage element) {
                return tokenStr;
            }

        };
    }

    @Override
    public String typeName() {
        return Token.CONSTANT;
    }
}

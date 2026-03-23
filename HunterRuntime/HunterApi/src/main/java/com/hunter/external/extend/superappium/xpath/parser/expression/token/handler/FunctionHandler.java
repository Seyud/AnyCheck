package com.hunter.external.extend.superappium.xpath.parser.expression.token.handler;

import com.hunter.external.extend.superappium.xpath.exception.XpathSyntaxErrorException;
import com.hunter.external.extend.superappium.xpath.parser.TokenQueue;
import com.hunter.external.extend.superappium.xpath.parser.expression.FunctionParser;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.Token;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenHandler;

public class FunctionHandler implements TokenHandler {
    @Override
    public SyntaxNode parseToken(String tokenStr) throws XpathSyntaxErrorException {
        return new FunctionParser(new TokenQueue(tokenStr)).parse();
    }

    @Override
    public String typeName() {
        return Token.FUNCTION;
    }
}

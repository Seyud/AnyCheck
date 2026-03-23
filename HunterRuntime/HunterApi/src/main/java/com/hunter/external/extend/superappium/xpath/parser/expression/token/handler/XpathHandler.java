package com.hunter.external.extend.superappium.xpath.parser.expression.token.handler;

import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xpath.XpathParser;
import com.hunter.external.extend.superappium.xpath.exception.XpathSyntaxErrorException;
import com.hunter.external.extend.superappium.xpath.model.XNode;
import com.hunter.external.extend.superappium.xpath.model.XNodes;
import com.hunter.external.extend.superappium.xpath.model.XpathEvaluator;
import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.Token;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenHandler;

public class XpathHandler implements TokenHandler {
    @Override
    public SyntaxNode parseToken(String tokenStr) throws XpathSyntaxErrorException {
        final XpathEvaluator xpathEvaluator = new XpathParser(tokenStr).parse();
        return new SyntaxNode() {
            @Override
            public Object calc(ViewImage element) {
                return xpathEvaluator.evaluate(new XNodes(XNode.e(element)));
            }
        };
    }

    @Override
    public String typeName() {
        return Token.XPATH;
    }
}

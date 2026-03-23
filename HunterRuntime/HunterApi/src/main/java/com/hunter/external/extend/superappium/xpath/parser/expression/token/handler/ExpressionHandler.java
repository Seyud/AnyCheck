 package com.hunter.external.extend.superappium.xpath.parser.expression.token.handler;

 import com.hunter.external.extend.superappium.xpath.exception.XpathSyntaxErrorException;
 import com.hunter.external.extend.superappium.xpath.parser.TokenQueue;
 import com.hunter.external.extend.superappium.xpath.parser.expression.ExpressionParser;
 import com.hunter.external.extend.superappium.xpath.parser.expression.SyntaxNode;
 import com.hunter.external.extend.superappium.xpath.parser.expression.token.Token;
 import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenHandler;

 public class ExpressionHandler implements TokenHandler {
     @Override
     public SyntaxNode parseToken(String tokenStr) throws XpathSyntaxErrorException {
         return new ExpressionParser(new TokenQueue(tokenStr)).parse();
     }

     @Override
     public String typeName() {
         return Token.EXPRESSION;
     }
 }


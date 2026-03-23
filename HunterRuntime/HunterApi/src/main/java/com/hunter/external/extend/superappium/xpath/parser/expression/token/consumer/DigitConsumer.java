package com.hunter.external.extend.superappium.xpath.parser.expression.token.consumer;

import com.hunter.external.extend.superappium.xpath.parser.TokenQueue;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenConsumer;

public class DigitConsumer implements TokenConsumer {
    public String consume(TokenQueue tokenQueue) {
        if (tokenQueue.matchesDigit()) {
            return tokenQueue.consumeDigit();
        }
        return null;
    }

    public int order() {
        return 50;
    }

    public String tokenType() {
        return "NUMBER";
    }
}
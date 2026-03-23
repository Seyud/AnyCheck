package com.hunter.external.extend.superappium.xpath.parser.expression.token.consumer;

import com.hunter.external.extend.superappium.xpath.parser.TokenQueue;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenConsumer;

public class BooleanConsumer implements TokenConsumer {
    public String consume(TokenQueue tokenQueue) {
        if (tokenQueue.matchesBoolean()) {
            return tokenQueue.consumeWord();
        }
        return null;
    }

    public int order() {
        return 70;
    }

    public String tokenType() {
        return "BOOLEAN";
    }
}

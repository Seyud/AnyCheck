package com.hunter.external.extend.superappium.xpath.parser.expression.token.consumer;

import com.hunter.external.extend.superappium.xpath.parser.TokenQueue;
import com.hunter.external.extend.superappium.xpath.parser.expression.token.TokenConsumer;

public class FunctionConsumer implements TokenConsumer {
    public String consume(TokenQueue tokenQueue) {
        if (tokenQueue.matchesFunction()) {
            return tokenQueue.consumeFunction();
        }
        return null;
    }

    public int order() {
        return 60;
    }

    public String tokenType() {
        return "FUNCTION";
    }
}

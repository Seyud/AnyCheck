package com.hunter.external.extend.superappium.xpath.parser.expression.node;

import com.hunter.external.extend.superappium.ViewImage;

public abstract class BooleanRevertUnit extends WrapperUnit {
    @Override
    public Object calc(ViewImage element) {
        return !((Boolean) wrap().calc(element));
    }

}
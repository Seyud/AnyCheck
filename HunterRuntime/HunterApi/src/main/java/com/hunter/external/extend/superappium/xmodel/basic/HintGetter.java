package com.hunter.external.extend.superappium.xmodel.basic;

import android.widget.TextView;

import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;

public class HintGetter implements ValueGetter {
    @Override
    public Object get(ViewImage viewImage) {
        TextView textView = (TextView) viewImage.getOriginView();
        CharSequence hint = textView.getHint();
        if (hint == null) {
            return null;
        }
        return hint.toString();
    }

    @Override
    public String attr() {
        return SuperAppium.hint;
    }

    @Override
    public boolean support(Class type) {
        return TextView.class.isAssignableFrom(type);
    }
}
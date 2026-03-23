package com.hunter.external.extend.superappium.xmodel.basic;


import android.widget.TextView;

import com.hunter.external.extend.superappium.SuperAppium;
import com.hunter.external.extend.superappium.ViewImage;
import com.hunter.external.extend.superappium.xmodel.ValueGetter;

public class TextGetter implements ValueGetter<String> {
    @Override
    public String get(ViewImage viewImage) {
        TextView textView = (TextView) viewImage.getOriginView();
        CharSequence text = textView.getText();
        if (text == null) {
            return null;
        }
        return text.toString();
    }

    @Override
    public boolean support(Class type) {
        return TextView.class.isAssignableFrom(type);
    }

    @Override
    public String attr() {
        return SuperAppium.text;
    }
}

package com.zhenxi.builder;

import com.zhenxi.meditor.ResourceIdXmlReader;

public class AttributeItem {
    private String namespace = "http://schemas.android.com/apk/res/android";
    private String name;
    private int resourceId = -1;
    private int type = 0;
    private Object value;

    public AttributeItem(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getName() {
        return this.name;
    }

    public int getResourceId() {
        if (this.resourceId > 0) {
            return this.resourceId;
        } else {
            if ("http://schemas.android.com/apk/res/android".equals(this.namespace)) {
                this.resourceId = ResourceIdXmlReader.parseIdFromXml(this.getName());
            }

            return this.resourceId;
        }
    }

    public int getType() {
        if (this.type == 0) {
            if (this.value instanceof String) {
                this.type = 3;
            } else if (this.value instanceof Boolean) {
                this.type = 18;
            }
        }

        return this.type;
    }

    public Object getValue() {
        return this.value;
    }

    public AttributeItem setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public AttributeItem setResourceId(int resourceId) {
        this.resourceId = resourceId;
        return this;
    }

    public AttributeItem setType(int type) {
        this.type = type;
        return this;
    }
}

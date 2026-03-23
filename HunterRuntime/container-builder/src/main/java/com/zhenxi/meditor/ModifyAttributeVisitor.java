package com.zhenxi.meditor;

import com.zhenxi.builder.AttributeItem;
import com.zhenxi.meditor.utils.Utils;

import org.jf.pxb.android.axml.NodeVisitor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ModifyAttributeVisitor extends NodeVisitor {
    private List<AttributeItem> hasBeenAddedAttributeList;
    private List<AttributeItem> mModifyAttributeList;
    private boolean isNewAddedTag;

    ModifyAttributeVisitor(NodeVisitor nv, List<AttributeItem> modifyAttributeList, boolean isNewAddedTag) {
        super(nv);
        this.hasBeenAddedAttributeList = new ArrayList<>();
        this.mModifyAttributeList = modifyAttributeList;
        this.isNewAddedTag = isNewAddedTag;
        if (isNewAddedTag) {
            this.modifyAttr();
        }

    }

    ModifyAttributeVisitor(NodeVisitor nv, List<AttributeItem> modifyAttributeList) {
        this(nv, modifyAttributeList, false);
    }

    public void addModifyAttributeItem(AttributeItem item) {
        if (this.mModifyAttributeList == null) {
            this.mModifyAttributeList = new ArrayList<>();
        }

        this.mModifyAttributeList.add(item);
    }

    public void attr(String ns, String name, int resourceId, int type, Object obj) {
        Object newObj = null;
        if (this.mModifyAttributeList != null) {
            Iterator var7 = this.mModifyAttributeList.iterator();

            while (var7.hasNext()) {
                AttributeItem attributeItem = (AttributeItem) var7.next();
                if (attributeItem != null && Utils.isEqual(ns, attributeItem.getNamespace()) && Utils.isEqual(name, attributeItem.getName())) {
                    this.hasBeenAddedAttributeList.add(attributeItem);
                    newObj = attributeItem.getValue();
                    break;
                }
            }
        }

        if (newObj == null) {
            newObj = obj;
        }

        super.attr(ns, name, resourceId, type, newObj);
    }

    public void end() {
        if (!this.isNewAddedTag) {
            this.modifyAttr();
        }

        super.end();
    }

    private void modifyAttr() {
        if (this.mModifyAttributeList != null) {
            Iterator var1 = this.mModifyAttributeList.iterator();

            while (var1.hasNext()) {
                AttributeItem attributeItem = (AttributeItem) var1.next();
                if (!this.hasBeenAddedAttributeList.contains(attributeItem)) {
                    super.attr(attributeItem.getNamespace(), attributeItem.getName(), attributeItem.getResourceId(), attributeItem.getType(), attributeItem.getValue());
                }
            }
        }

    }
}
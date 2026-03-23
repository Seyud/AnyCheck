package com.zhenxi.meditor;


import com.zhenxi.builder.AttributeItem;

import org.jf.pxb.android.axml.NodeVisitor;

import java.util.ArrayList;
import java.util.List;

public class MetaDataVisitor extends ModifyAttributeVisitor {
    MetaDataVisitor(NodeVisitor nv, ManifestModificationProperty.MetaData metaData) {
        super(nv, convertToAttr(metaData), true);
    }

    private static List<AttributeItem> convertToAttr(ManifestModificationProperty.MetaData metaData) {
        if (metaData == null) {
            return null;
        } else {
            ArrayList<AttributeItem> list = new ArrayList();
            list.add(new AttributeItem("name", metaData.getName()));
            list.add(new AttributeItem("value", metaData.getValue()));
            return list;
        }
    }
}

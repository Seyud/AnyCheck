package com.zhenxi.meditor;

import com.zhenxi.builder.AttributeItem;
import com.zhenxi.meditor.utils.Utils;

import org.jf.pxb.android.axml.NodeVisitor;

class UserPermissionTagVisitor extends NodeVisitor {
    private IUsesPermissionGetter permissionGetter;

    UserPermissionTagVisitor(NodeVisitor nv, IUsesPermissionGetter permissionGetter, String permissionTobeAdded) {
        super(nv);
        this.permissionGetter = permissionGetter;
        if (!Utils.isNullOrEmpty(permissionTobeAdded)) {
            AttributeItem attributeItem = new AttributeItem("name", permissionTobeAdded);
            super.attr(attributeItem.getNamespace(), attributeItem.getName(), attributeItem.getResourceId(), attributeItem.getType(), attributeItem.getValue());
        }

    }

    public void attr(String ns, String name, int resourceId, int type, Object obj) {
        if (obj instanceof String && this.permissionGetter != null) {
            this.permissionGetter.onPermissionGetted((String)obj);
        }

        super.attr(ns, name, resourceId, type, obj);
    }

    public interface IUsesPermissionGetter {
        void onPermissionGetted(String var1);
    }
}
package com.zhenxi.meditor;

import org.jf.pxb.android.axml.NodeVisitor;

public class ServiceTagVisitor extends NodeVisitor {

    ServiceTagVisitor(NodeVisitor nv) {
        super(nv);
    }

    @Override
    public void attr(String ns, String name, int resourceId, int type, Object obj) {
        // 这里如果是找到了isolatedProcess这个属性, 直接修改成为false
        if ("isolatedProcess".equals(name)) {
            super.attr(ns, name, resourceId, type, false);
        } else {
            super.attr(ns, name, resourceId, type, obj);
        }
    }
}

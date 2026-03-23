package com.zhenxi.meditor;

import com.zhenxi.meditor.utils.Log;

import org.jf.pxb.android.axml.NodeVisitor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class ManifestTagVisitor extends ModifyAttributeVisitor {
    private ManifestModificationProperty properties;
    private List<String> hasIncludedUsesPermissionList = new ArrayList();
    private UserPermissionTagVisitor.IUsesPermissionGetter addedPermissionGetter;

    public ManifestTagVisitor(NodeVisitor nv, ManifestModificationProperty properties) {
        super(nv, properties.getManifestAttributeList());
        this.properties = properties;
    }

    public NodeVisitor child(String ns, String name) {
//        System.out.println(" ManifestTagVisitor child  --> ns = " + ns + " name = " + name);
        NodeVisitor child;
        if (ns != null && "uses-permission".equals(name)) {
            child = super.child((String)null, "uses-permission");
            return new UserPermissionTagVisitor(child, (UserPermissionTagVisitor.IUsesPermissionGetter)null, ns);
        } else {
            child = super.child(ns, name);
            if ("application".equals(name)) {
                return new ApplicationTagVisitor(child, this.properties.getApplicationAttributeList(), this.properties.getMetaDataList());
            } else {
                return (NodeVisitor)("uses-permission".equals(name) ? new UserPermissionTagVisitor(child, this.getUsesPermissionGetter(), (String)null) : child);
            }
        }
    }

    public void attr(String ns, String name, int resourceId, int type, Object obj) {
        Log.d(" ManifestTagVisitor attr  --> ns = " + ns + " name = " + name + " resourceId=" + resourceId + " obj = " + obj);
        super.attr(ns, name, resourceId, type, obj);
    }

    public void end() {
        List<String> list = this.properties.getUsesPermissionList();
        if (list != null && list.size() > 0) {
            Iterator var2 = list.iterator();

            while(var2.hasNext()) {
                String permissionName = (String)var2.next();
                if (!this.hasIncludedUsesPermissionList.contains(permissionName)) {
                    this.child(permissionName, "uses-permission");
                }
            }
        }

        super.end();
    }

    private UserPermissionTagVisitor.IUsesPermissionGetter getUsesPermissionGetter() {
        if (this.addedPermissionGetter == null) {
            this.addedPermissionGetter = (permissionName) -> {
                if (!this.hasIncludedUsesPermissionList.contains(permissionName)) {
                    this.hasIncludedUsesPermissionList.add(permissionName);
                }

            };
        }

        return this.addedPermissionGetter;
    }
}
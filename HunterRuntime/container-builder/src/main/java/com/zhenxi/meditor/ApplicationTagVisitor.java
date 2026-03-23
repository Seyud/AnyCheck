package com.zhenxi.meditor;

import com.zhenxi.builder.AttributeItem;
import com.zhenxi.builder.ContainerBuilder;

import org.jf.pxb.android.axml.NodeVisitor;

import java.util.Iterator;
import java.util.List;

public class ApplicationTagVisitor extends ModifyAttributeVisitor {
    private List<ManifestModificationProperty.MetaData> metaDataList;
    private ManifestModificationProperty.MetaData curMetaData;
    private static final String META_DATA_FLAG = "meta_data_flag";

    ApplicationTagVisitor(NodeVisitor nv, List<AttributeItem> modifyAttributeList, List<ManifestModificationProperty.MetaData> metaDataList) {
        super(nv, modifyAttributeList);
        this.metaDataList = metaDataList;
    }

    public NodeVisitor child(String ns, String name) {
        if ("meta_data_flag".equals(ns)) {
            NodeVisitor nv = super.child((String)null, name);
            if (this.curMetaData != null) {
                return new MetaDataVisitor(nv, new ManifestModificationProperty.MetaData(this.curMetaData.getName(), this.curMetaData.getValue()));
            }
        }
        if(ContainerBuilder.mBuilderContext.cmd.hasOption("removeiso")) {
            if ("service".equals(name)) {
                // 找到service的tag, 然后修改isolatedProcess为false
                NodeVisitor child = super.child(ns, name);
                return new ServiceTagVisitor(child);
            }
        }
        return super.child(ns, name);
    }

    private void addChild(ManifestModificationProperty.MetaData data) {
        this.curMetaData = data;
        this.child("meta_data_flag", "meta-data");
        this.curMetaData = null;
    }

    public void end() {
        if (this.metaDataList != null) {
            Iterator var1 = this.metaDataList.iterator();

            while(var1.hasNext()) {
                ManifestModificationProperty.MetaData data = (ManifestModificationProperty.MetaData)var1.next();
                this.addChild(data);
            }
        }

        super.end();
    }
}
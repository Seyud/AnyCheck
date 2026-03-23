package com.zhenxi.meditor;

import com.zhenxi.builder.AttributeItem;

import java.util.ArrayList;
import java.util.List;

public class ManifestModificationProperty {

    private final List<String> usesPermissionList = new ArrayList<>();
    private final List<MetaData> metaDataList = new ArrayList<>();
    private final List<MetaData> metaUsesSdkList = new ArrayList<>();
    private final List<AttributeItem> applicationAttributeList = new ArrayList<>();
    private final List<AttributeItem> manifestAttributeList = new ArrayList<>();

    public ManifestModificationProperty() {
    }

    public List<MetaData> getUsesSdkAttributeList() {
        return this.metaUsesSdkList;
    }



    public List<String> getUsesPermissionList() {
        return this.usesPermissionList;
    }

    public ManifestModificationProperty addUsesPermission(String permissionName) {
        this.usesPermissionList.add(permissionName);
        return this;
    }

    public List<AttributeItem> getApplicationAttributeList() {
        return this.applicationAttributeList;
    }

    public ManifestModificationProperty addApplicationAttribute(AttributeItem item) {
        this.applicationAttributeList.add(item);
        return this;
    }

    public void removeApplicationAttribute(String it) {
        //this.applicationAttributeList.removeIf(it -> it.getName().equals(item));
        int index = -1 ;
        for(int i=0;i<this.applicationAttributeList.size();i++){
            if(this.applicationAttributeList.get(i).getName().equals(it)){
                index = i;
                break;
            }
        }
        if(index!=-1){
            System.out.println("remove item  "+applicationAttributeList.get(index).getName());
            this.applicationAttributeList.remove(index);
        }
    }

    public List<MetaData> getMetaDataList() {
        return this.metaDataList;
    }

    public ManifestModificationProperty addMetaData(MetaData data) {
        this.metaDataList.add(data);
        return this;
    }

    public List<AttributeItem> getManifestAttributeList() {
        return this.manifestAttributeList;
    }

    public ManifestModificationProperty addManifestAttribute(AttributeItem item) {
        this.manifestAttributeList.add(item);
        return this;
    }

    /**
     * 判断是否存在指定的Key
     */
    public boolean applicationHasItem(String key) {
        for(AttributeItem item:applicationAttributeList){
            if(item.getName().equals(key)){
                return true;
            }
        }
        return false;
    }

    public Object getValue(String key) {
        for(AttributeItem item:applicationAttributeList){
            if(item.getName().equals(key)){
                return item.getValue();
            }
        }
        return null;
    }

    public static class MetaData {
        private String name;
        private String value;

        public MetaData(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return this.name;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }

}

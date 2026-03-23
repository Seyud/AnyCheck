package com.zhenxi.builder;


import com.zhenxi.meditor.ManifestModificationProperty;
import com.zhenxi.meditor.ManifestTagVisitor;

import org.jf.pxb.android.axml.AxmlReader;
import org.jf.pxb.android.axml.AxmlVisitor;
import org.jf.pxb.android.axml.AxmlWriter;
import org.jf.pxb.android.axml.NodeVisitor;

import java.io.IOException;

public class ManifestHandlers {

    public static byte[] editManifestXML(byte[] manifestXmlData,
                                         ManifestModificationProperty properties) throws IOException {
        AxmlReader reader = new AxmlReader(manifestXmlData);
        AxmlWriter writer = new AxmlWriter();

        AxmlVisitor axmlVisitor = new AxmlVisitor(writer) {
            public NodeVisitor child(String ns, String name) {
                NodeVisitor child = super.child(ns, name);
                return new ManifestTagVisitor(child, properties);
            }
        };
        reader.accept(axmlVisitor);

        return writer.toByteArray();
    }

}
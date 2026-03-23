package com.zhenxi.meditor;

import com.zhenxi.meditor.utils.Log;
import com.zhenxi.meditor.utils.Utils;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
public class ResourceIdXmlReader {
    private static final Map<String, Integer> attrCachedMap = new HashMap();

    public ResourceIdXmlReader() {
    }

    public static int parseIdFromXml(String name) {
        String filePath = "assets/ZhenxiPublic.xml";
        InputStream inputStream = Utils.getInputStreamFromFile(filePath);

        try {
            Integer cacherId = (Integer)attrCachedMap.get(name);
            if (cacherId != null && cacherId > 0) {
                return cacherId;
            }
            String id = findIdFromXmlFile(inputStream, "attr", name);

            Log.d(String.format("name = %s, id = %s", name, id));
            if (id != null) {
                int idInt = Integer.parseInt(id.substring(2), 16);
                attrCachedMap.put(name, idInt);
                return idInt;
            }
        } catch (Exception var6) {
            var6.printStackTrace();
        }

        return -1;
    }

    private static String findIdFromXmlFile(InputStream inputStream, String type, String name) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;

        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException var15) {
            var15.printStackTrace();
        }

        if (builder == null) {
            System.out.println("parse xml failed, DocumentBuilder is null");
            return null;
        } else {
            Document doc = null;

            try {
                doc = builder.parse(inputStream);
            } catch (IOException var12) {
                var12.printStackTrace();
            } catch (SAXException var13) {
                var13.printStackTrace();
            } catch (IllegalArgumentException var14) {
                var14.printStackTrace();
            }

            if (doc == null) {
                System.out.println("parse xml failed, Document is null");
                return null;
            } else {
                NodeList nodeList = doc.getElementsByTagName("public");
                int length = nodeList.getLength();

                for(int i = 0; i < length; ++i) {
                    Node node = nodeList.item(i);
                    NamedNodeMap nnm = node.getAttributes();
                    String id = findIdByNameAndType(nnm, type, name);
                    if (id != null) {
                        return id;
                    }
                }

                return null;
            }
        }
    }

    private static String findIdByNameAndType(NamedNodeMap map, String type, String name) {
        int length = map.getLength();
        String nodeName = null;
        String nodeType = null;
        String nodeId = null;

        for(int i = 0; i < length; ++i) {
            Node node = map.item(i);
            if (node != null) {
                String attrName = node.getNodeName();
                String attrValue = node.getNodeValue();
                if ("type".equals(attrName)) {
                    nodeType = attrValue;
                } else if ("name".equals(attrName)) {
                    nodeName = attrValue;
                } else if ("id".equals(attrName)) {
                    nodeId = attrValue;
                }
            }
        }

        if (nodeName != null && nodeName.equals(name) && nodeType != null && nodeType.equals(type)) {
            try {
                return nodeId;
            } catch (Exception var11) {
                var11.printStackTrace();
            }
        }

        return null;
    }
}
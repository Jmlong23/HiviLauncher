package com.ljm.audiotoollib.upnpserver.utils;

import android.util.Base64;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Created by Jay on 2015/9/8 0008.
 */
public class DomHelper {
    private static final String TAG = "DomHelper";
    private static final String PREFIX = "GZ:";
    private static final boolean ENABLE_VERBOSE_LOG = false;
    private static final ThreadLocal<DocumentBuilder> DOC_BUILDER = new ThreadLocal<>();

    //同时保留 gzip+base64 压缩能力（{@code GZ:} 前缀格式）作为通用工具。
    // ==================== GZ 压缩/解压 ====================

    public static boolean isGzipBase64Data(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    public static String decompress(String data) {
        if (!isGzipBase64Data(data)) return data;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decode(data.substring(PREFIX.length()), Base64.NO_WRAP));
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = gis.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toString("UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "decompress failed, returning raw data", e);
            return data;
        }
    }

    /**
     * 将 albumArtURI 转为可被 Gson 解析的 JSON 字符串。
     * GZ: 压缩格式先解压；普通格式走 URL decode + &amp;quot; 替换。
     */
    public static String decodeAlbumArtUriToJson(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        if (isGzipBase64Data(raw)) {
            return decompress(raw);
        }
        try {
            return URLDecoder.decode(raw.replace("&quot;", "\""), "UTF-8");
        } catch (Exception e) {
            return raw;
        }
    }


    public static String GetDocumentItem(String xml, String key, int index) {

        try{
            Document doc = getDocumentBuilder().parse(
                    new InputSource(
                            new StringReader(xml.trim())
                    )
            );

            Node tmpNode;
            Node textNode;
            String ret = "";

            NodeList nList = doc.getElementsByTagName(key);
            if(nList != null) {
                tmpNode = nList.item(index);
                if(tmpNode != null) {
                    textNode = tmpNode.getFirstChild();
                    if(textNode != null && textNode.getNodeType() == Node.TEXT_NODE) {
                        if (ENABLE_VERBOSE_LOG) {
                            short type = textNode.getNodeType();
                            Log.i(TAG, "type: " + type);
                        }
                        ret = textNode.getNodeValue();
                    }
                }
            }

            return ret;

        } catch (Exception e){
            e.printStackTrace();
        }

        return "";
    }

    private static DocumentBuilder getDocumentBuilder() throws Exception {
        DocumentBuilder builder = DOC_BUILDER.get();
        if (builder != null) {
            return builder;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        builder = factory.newDocumentBuilder();
        DOC_BUILDER.set(builder);
        return builder;
    }

    public static String queryXML(String xml)
    {
        try{
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();

            Document doc = documentBuilder.parse(
                    new InputSource(
                            new StringReader(xml.trim())
                    )
            );

            NodeList nList = doc.getElementsByTagName("PlayList");
            for(int i = 0;i < nList.getLength();i++)
            {
                Element personElement = (Element) nList.item(i);
                NodeList childNoList = personElement.getChildNodes();
                for(int j = 0;j < childNoList.getLength();j++)
                {
                    Node childNode = childNoList.item(j);
                    if(childNode.getNodeType() == Node.ELEMENT_NODE)
                    {
                        Element childElement = (Element) childNode;
                        if("ListName".equals(childElement.getNodeName())){
                            String value = childElement.getFirstChild().getNodeValue();
                            Log.i(TAG, "ListName value: " + value);
                        }
                        else if("ListInfo".equals(childElement.getNodeName())){
                            String value = childElement.getFirstChild().getNodeValue();
                            Log.i(TAG, "ListInfo value: " + value);
                        }

                    }
                }
            }

        } catch (Exception e){
            e.printStackTrace();
        }

        return "";
    }
}

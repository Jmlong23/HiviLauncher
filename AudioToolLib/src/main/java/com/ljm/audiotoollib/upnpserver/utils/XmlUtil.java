package com.ljm.audiotoollib.upnpserver.utils;


import android.text.TextUtils;
import android.util.Log;

import com.ljm.audiotoollib.upnpserver.entity.LPPlayHeader;
import com.ljm.audiotoollib.upnpserver.entity.PlayMusicListType;
import com.ljm.audiotoollib.upnpserver.entity.TrackINFO_Type;

import java.util.List;
import java.util.regex.Pattern;

public class XmlUtil {
    public XmlUtil() {
    }

    public static String Encode(String var0) {
        return TextUtils.isEmpty(var0) ? var0 : var0.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;");
    }

    public static String Decode(String var0) {
        return TextUtils.isEmpty(var0) ? var0 : var0.replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'").replace("&quot;", "\"").replace("&amp;", "&");
    }

    public static String getCommonStr(String var0) {
        if (TextUtils.isEmpty(var0)) {
            return "";
        } else {
            char[] var1 = var0.toCharArray();
            boolean var2 = false;

            for(int var3 = 0; var3 < var1.length; ++var3) {
                char var4;
                if ((var4 = var1[var3]) > 0 && var4 < ' ') {
                    var1[var3] = ' ';
                    var2 = true;
                }
            }

            if (var2) {
                return (new String(var1)).trim();
            } else {
                return var0;
            }
        }
    }

    public static String createXmlString(PlayMusicListType var0) {
        LPPlayHeader var1;
        LPPlayHeader var10000 = var1 = var0.getHeader();
        List var2 = var0.getPQMusicList();
        if (var10000 == null) {
            Log.i("LPQueueXmlCreator", "alarm_backqueue: header is null");
            return "";
        } else {
            StringBuffer var4;
            StringBuffer var10001 = var4 = new StringBuffer();
            appendStrings(var4, "<?xml version=\"1.0\" ?>\n");
            appendStrings(var4, "<PlayList>\n");
            appendStrings(var4, "<ListName>" + Encode(getCommonStr(var1.getHeadTitle())) + "</ListName>\n");
            appendStrings(var4, "<ListInfo>\n");
            appendStrings(var10001, "<Radio>" + var1.getMediaType().equals("STATION-NETWORK") + "</Radio>\n");
            int var5;
            if (var2 == null) {
                var5 = 0;
            } else {
                var5 = var2.size();
            }

            String var6 = var1.getSearchUrl();
            appendStrings(var4, "<SourceName>" + Encode(getCommonStr(var1.getMediaSource())) + "</SourceName>\n");
            appendStrings(var4, "<SearchUrl>" + Encode(getCommonStr(var6)) + "</SearchUrl>\n");
            appendStrings(var4, "<TrackNumber>" + var5 + "</TrackNumber>\n");
            String var13;
            if (!TextUtils.isEmpty(var13 = var1.getQuality()) && !isNumber(var13)) {
                appendStrings(var4, "<requestQuality>" + var13 + "</requestQuality>\n");
            } else {
                StringBuilder var14 = (new StringBuilder()).append("<Quality>");
                if (TextUtils.isEmpty(var13)) {
                    var6 = "0";
                } else {
                    var6 = var1.getQuality();
                }

                appendStrings(var4, var14.append(var6).append("</Quality>\n").toString());
            }

            appendStrings(var4, "<UpdateTime>0</UpdateTime>\n");
            appendStrings(var4, "<LastPlayIndex>" + var0.getCurPlayIndex() + "</LastPlayIndex>\n");
            appendStrings(var4, "<SwitchPageMode>0</SwitchPageMode>\n");
            appendStrings(var4, "<CurrentPage>" + var1.getCurrentPage() + "</CurrentPage>\n");
            appendStrings(var4, "<TotalPages>" + var1.getTotalPage() + "</TotalPages>\n");
            appendStrings(var4, "</ListInfo>\n");
            if (var2 != null && var2.size() > 0) {
                List var10;
                List var18 = var10 = var0.getPQMusicList();
                String var9 = var0.getHeader().getCreator();
                String var11 = var0.getHeader().getMediaSource();
                if (var18 != null && var10.size() > 0) {
                    appendStrings(var4, "<Tracks>\n");
                    int var12 = 0;

                    while (var12 < var10.size()) {
                        TrackINFO_Type var15 = (TrackINFO_Type) var10.get(var12);
                        StringBuffer var16;
                        StringBuffer var19 = var16 = new StringBuffer();
                        String var21 = var9;
                        TrackINFO_Type var10002 = var15;
                        appendStrings(var4, "<Track" + ++var12 + ">\n");
                        var16.append("<Metadata>\n");
                        try {
                            var19.append(getMetadata(var21, var10002));
                        } catch (Exception var8) {
                            var8.printStackTrace();
                            var16.append("");
                        }

                        var16.append("</Metadata>\n");
                        var16.append("<Id>" + var15.getMusicDataBean().getId() + "</Id>\n");
                        var16.append("<Source>" + Encode(getCommonStr(var11)) + "</Source>\n");
                        var16.append("</Track" + var12 + ">\n");
                        appendStrings(var4, var16.toString());
                    }

                    appendStrings(var4, "</Tracks>\n");
                }
            }

            appendStrings(var4, "</PlayList>\n");
            return var4.toString();
        }
    }


    public static void appendStrings(StringBuffer var0, String var1) {
        var0.append(var1);
    }


    public static boolean isNumber(String var0) {
        return Pattern.compile("[0-9]*").matcher(var0).matches();
    }


    public static String getMetadata(String var0, TrackINFO_Type var1) {
        if (var1 == null) {
            return "";
        } else {
            String var2 = "" + "<DIDL-Lite ";
            var2 = var2 + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ";
            var2 = var2 + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ";
            var2 = var2 + "xmlns:song=\"www.wiimu.com/song/\" ";
            var2 = var2 + "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"> ";
            var2 = var2 + "<upnp:class>object.item.audioItem.musicTrack</upnp:class> ";
            var2 = var2 + "<item> ";
            var2 = var2 + "<song:bitrate>0</song:bitrate> ";
            var2 = var2 + "<song:id>" + var1.getMusicDataBean().getId() + "</song:id>";
            var2 = var2 + "<song:singerid>0</song:singerid>";
            var2 = var2 + "<song:albumid>" + var1.getMusicDataBean().getId() + "</song:albumid>";
            String var3;
//            if (var1.getMusicDataBean().() != 1L) {
//                var3 = var1.getTrackDuration() + "";
//            } else {
//                var3 = "0";
//            }
            var3 = "0";

            var2 = var2 + "<res protocolInfo=\"http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;\" duration=\"" + var3 + "\">" + Encode(getCommonStr(var1.getMusicDataBean().getPlayUrl())) + "</res>";
            var2 = var2 + "<dc:title>" + Encode(getCommonStr(var1.getMusicDataBean().getName())) + "</dc:title> ";
            var2 = var2 + "<upnp:artist>" + Encode(getCommonStr(var1.getMusicDataBean().getArtist())) + "</upnp:artist> ";
            var0 = var2 + "<upnp:creator>" + Encode(getCommonStr(var0)) + "</upnp:creator> ";
            var0 = var0 + "<upnp:album>" + Encode(getCommonStr(var1.getMusicDataBean().getAlg())) + "</upnp:album> ";
            StringBuilder var4 = (new StringBuilder()).append(var0).append("<upnp:albumArtURI>");
//            var0 = var4.append(Encode(getCommonStr(var1.getTrackImage() == null ? "" : var1.getTrackImage()))).append("</upnp:albumArtURI> ").toString();
            String albumArtURI = "";
            try {
                albumArtURI = var1.getMediaInfo() == null ? "" : var1.getMediaInfo().getAlbumArtURI();
            } catch (Exception ignore) {
            }
            var0 = var4.append(Encode(getCommonStr(albumArtURI))).append("</upnp:albumArtURI> ").toString();
            var0 = var0 + "</item> ";
            return Encode(var0 + "</DIDL-Lite> ");
        }
    }
}

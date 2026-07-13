package com.ljm.audiotoollib.upnpserver.entity;

import android.text.TextUtils;

import com.ljm.audiotoollib.upnpserver.utils.XmlUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaInfoParser {
    public MediaInfoParser() {
    }

    public static String convert2Xml(MediaInfo albumInfo, boolean isRadio) {
        String isRadio1 = "" + "<DIDL-Lite ";
        isRadio1 = isRadio1 + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ";
        isRadio1 = isRadio1 + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ";
        isRadio1 = isRadio1 + "xmlns:song=\"www.wiimu.com/song/\" ";
        isRadio1 = isRadio1 + "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"> ";
        isRadio1 = isRadio1 + "<upnp:class>object.item.audioItem.musicTrack</upnp:class> ";
        isRadio1 = isRadio1 + "<item> ";
        isRadio1 = isRadio1 + "<song:bitrate>" + albumInfo.getBitrate() + "</song:bitrate> ";
        isRadio1 = isRadio1 + "<song:id>" + albumInfo.getSong_id() + "</song:id>";
        isRadio1 = isRadio1 + "<song:singerid>" + albumInfo.getArtist_id() + "</song:singerid>";
        isRadio1 = isRadio1 + "<song:albumid>" + albumInfo.getAlbum_id() + "</song:albumid>";
        String var2;
        if (albumInfo.getTotalTime() != 1L) {
            var2 = albumInfo.getTotalTime() + "";
        } else {
            var2 = "0";
        }

        isRadio1 = isRadio1 + "<res protocolInfo=\"http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;\" duration=\"" + var2 + "\">" + XmlUtil.Encode(XmlUtil.getCommonStr(albumInfo.getPlayUri())) + "</res>";
        isRadio1 = isRadio1 + "<dc:title>" + XmlUtil.Encode(XmlUtil.getCommonStr(albumInfo.getTitle())) + "</dc:title> ";
        isRadio1 = isRadio1 + "<upnp:artist>" + XmlUtil.Encode(XmlUtil.getCommonStr(albumInfo.getArtist())) + "</upnp:artist> ";
        isRadio1 = isRadio1 + "<upnp:creator>" + XmlUtil.Encode(XmlUtil.getCommonStr(albumInfo.getCreator())) + "</upnp:creator> ";
        isRadio1 = isRadio1 + "<upnp:album>" + XmlUtil.Encode(XmlUtil.getCommonStr(albumInfo.getAlbum())) + "</upnp:album> ";
        StringBuilder isRadio2 = (new StringBuilder()).append(isRadio1).append("<upnp:albumArtURI>");
        String albumInfo1 = isRadio2.append(XmlUtil.Encode(XmlUtil.getCommonStr(albumInfo.getAlbumArtURI() == null ? "" : albumInfo.getAlbumArtURI()))).append("</upnp:albumArtURI> ").toString();
        albumInfo1 = albumInfo1 + "</item> ";
        return XmlUtil.Encode(albumInfo1 + "</DIDL-Lite> ");
    }

    public static synchronized MediaInfo convert2MediaInfo(String xml) throws Exception {
        MediaInfo var1;
        var1 = new MediaInfo();
        if (TextUtils.isEmpty(xml)) {
            return var1;
        } else {
            String var10000 = xml;
            String var2 = "";

            boolean var10001;
            int var10002;
            Exception var59;
            boolean var60;
            String var61;
            label389: {
                label388: {
                    label395: {
                        try {
                            var60 = var10000.contains("<song:description>");
                        } catch (Exception var49) {
                            var59 = var49;
                            var10001 = false;
                            break label395;
                        }

                        if (!var60) {
                            break label389;
                        }

                        try {
                            var10000 = xml;
                            var61 = xml;
                            var10002 = xml.indexOf("<song:description>");
                        } catch (Exception var48) {
                            var59 = var48;
                            var10001 = false;
                            break label395;
                        }

                        int var52 = var10002;

                        try {
                            var10000 = var10000.substring(var52, var61.indexOf("</song:description>")).replace("<song:description>", "");
                            break label388;
                        } catch (Exception var47) {
                            var59 = var47;
                            var10001 = false;
                        }
                    }

                    var59.printStackTrace();
                    var2 = "";
                    break label389;
                }

                var2 = var10000;
            }

            label373: {
                try {
                    var10000 = xml.replaceAll("<upnp:artist\\s*[\\w\\W]*>(.*)</upnp:artist>", "<upnp:artist>$1</upnp:artist>");
                } catch (Exception var46) {
                    break label373;
                }

                xml = var10000;
            }

            label369: {
                try {
                    var10000 = xml.replaceAll("<upnp:albumArtURI\\s*[\\w\\W]*>(.*)</upnp:albumArtURI>", "<upnp:albumArtURI>$1</upnp:albumArtURI>");
                } catch (Exception var45) {
                    break label369;
                }

                xml = var10000;
            }

            var10000 = xml;
            String var3 = "";

            label416: {
                try {
                    var60 = var10000.contains("<dc:title>");
                } catch (Exception var44) {
                    var10001 = false;
                    break label416;
                }

                if (var60) {
                    try {
                        var10000 = xml;
                        var61 = xml;
                        var10002 = xml.indexOf("<dc:title>");
                    } catch (Exception var43) {
                        var10001 = false;
                        break label416;
                    }

                    int var4 = var10002;

                    try {
                        var10000 = var10000.substring(var4, var61.indexOf("</dc:title>")).replace("<dc:title>", "");
                    } catch (Exception var42) {
                        var10001 = false;
                        break label416;
                    }

                    var3 = var10000;

                    try {
                        var10000 = XmlUtil.Decode(var10000).replace("<![CDATA[", "").replace("]]>", "");
                    } catch (Exception var41) {
                        var10001 = false;
                        break label416;
                    }

                    var3 = var10000;
                }
            }

            var10000 = xml;
            String var53 = "";

            label343: {
                label408: {
                    int var5;
                    label398: {
                        try {
                            var60 = var10000.contains("<upnp:artist>");
                        } catch (Exception var40) {
                            var10001 = false;
                            break label398;
                        }

                        if (!var60) {
                            break label343;
                        }

                        try {
                            var10000 = xml;
                            var61 = xml;
                            var10002 = xml.indexOf("<upnp:artist>");
                        } catch (Exception var39) {
                            var10001 = false;
                            break label398;
                        }

                        var5 = var10002;

                        try {
                            var10000 = var10000.substring(var5, var61.indexOf("</upnp:artist>")).replace("<upnp:artist>", "");
                        } catch (Exception var38) {
                            var10001 = false;
                            break label398;
                        }

                        var53 = var10000;

                        try {
                            var10000 = XmlUtil.Decode(var10000).replace("<![CDATA[", "").replace("]]>", "");
                            break label408;
                        } catch (Exception var37) {
                            var10001 = false;
                        }
                    }

                    try {
                        var60 = xml.contains("<dc:creator>");
                    } catch (Exception var36) {
                        var10001 = false;
                        break label343;
                    }

                    if (!var60) {
                        break label343;
                    }

                    try {
                        var10000 = xml;
                        var61 = xml;
                        var10002 = xml.indexOf("<dc:creator>");
                    } catch (Exception var35) {
                        var10001 = false;
                        break label343;
                    }

                    var5 = var10002;

                    try {
                        var10000 = var10000.substring(var5, var61.indexOf("</dc:creator>")).replace("<dc:creator>", "");
                    } catch (Exception var34) {
                        var10001 = false;
                        break label343;
                    }

                    var53 = var10000;

                    try {
                        var10000 = XmlUtil.Decode(var10000).replace("<![CDATA[", "").replace("]]>", "");
                    } catch (Exception var33) {
                        var10001 = false;
                        break label343;
                    }
                }

                var53 = var10000;
            }

            var10000 = xml;
            String var54 = "";

            label417: {
                try {
                    var60 = var10000.contains("<upnp:album>");
                } catch (Exception var32) {
                    var10001 = false;
                    break label417;
                }

                if (var60) {
                    try {
                        var10000 = xml;
                        var61 = xml;
                        var10002 = xml.indexOf("<upnp:album>");
                    } catch (Exception var31) {
                        var10001 = false;
                        break label417;
                    }

                    int var6 = var10002;

                    try {
                        var10000 = var10000.substring(var6, var61.indexOf("</upnp:album>")).replace("<upnp:album>", "");
                    } catch (Exception var30) {
                        var10001 = false;
                        break label417;
                    }

                    var54 = var10000;

                    try {
                        var10000 = XmlUtil.Decode(var10000).replace("<![CDATA[", "").replace("]]>", "");
                    } catch (Exception var29) {
                        var10001 = false;
                        break label417;
                    }

                    var54 = var10000;
                }
            }

            var10000 = xml;
            String var55 = "";

            label418: {
                try {
                    var60 = var10000.contains("<upnp:albumArtURI>");
                } catch (Exception var28) {
                    var10001 = false;
                    break label418;
                }

                if (var60) {
                    try {
                        var10000 = xml;
                        var61 = xml;
                        var10002 = xml.indexOf("<upnp:albumArtURI>");
                    } catch (Exception var27) {
                        var10001 = false;
                        break label418;
                    }

                    int var7 = var10002;

                    try {
                        var10000 = var10000.substring(var7, var61.indexOf("</upnp:albumArtURI>")).replace("<upnp:albumArtURI>", "");
                    } catch (Exception var26) {
                        var10001 = false;
                        break label418;
                    }

                    var55 = var10000;

                    try {
                        var10000 = XmlUtil.Decode(var10000).replace("<![CDATA[", "").replace("]]>", "");
                    } catch (Exception var25) {
                        var10001 = false;
                        break label418;
                    }

                    var55 = var10000;
                }
            }

            var10000 = xml;
            String var56 = "";

            int var8;
            label419: {
                try {
                    var60 = var10000.contains("<song:id>");
                } catch (Exception var24) {
                    var10001 = false;
                    break label419;
                }

                if (var60) {
                    try {
                        var10000 = xml;
                        var61 = xml;
                        var10002 = xml.indexOf("<song:id>");
                    } catch (Exception var23) {
                        var10001 = false;
                        break label419;
                    }

                    var8 = var10002;

                    try {
                        var10000 = var10000.substring(var8, var61.indexOf("</song:id>")).replace("<song:id>", "");
                    } catch (Exception var22) {
                        var10001 = false;
                        break label419;
                    }

                    var56 = var10000;
                }
            }

            int var10003;
            String var62;
            MediaInfo var63;
            label403: {
                try {
                    var60 = xml.contains("<song:controls>");
                } catch (Exception var21) {
                    var10001 = false;
                    break label403;
                }

                if (var60) {
                    label413: {
                        try {
                            var63 = var1;
                            var61 = xml;
                            var62 = xml;
                            var10003 = xml.indexOf("<song:controls>");
                        } catch (Exception var20) {
                            var10001 = false;
                            break label413;
                        }

                        var8 = var10003;

                        try {
                            var63.setControlHex(Integer.parseInt(var61.substring(var8, var62.indexOf("</song:controls>")).replace("<song:controls>", "")));
                        } catch (Exception var19) {
                            var10001 = false;
                        }
                    }
                }
            }

            if (xml.contains("<song:subid>")) {
                var8 = xml.indexOf("<song:subid>");
                var1.setSubid(xml.substring(var8, xml.indexOf("</song:subid>")).replace("<song:subid>", ""));
            }

            label230: {
                label404: {
                    try {
                        var60 = xml.contains("<dc:subtitle>");
                    } catch (Exception var18) {
                        var59 = var18;
                        var10001 = false;
                        break label404;
                    }

                    if (!var60) {
                        break label230;
                    }

                    try {
                        var63 = var1;
                        var61 = xml;
                        var62 = xml;
                        var10003 = xml.indexOf("<dc:subtitle");
                    } catch (Exception var17) {
                        var59 = var17;
                        var10001 = false;
                        break label404;
                    }

                    var8 = var10003;

                    try {
                        var63.setSubTitle(var61.substring(var8, var62.indexOf("</dc:subtitle")).replace("<dc:subtitle>", ""));
                        break label230;
                    } catch (Exception var16) {
                        var59 = var16;
                        var10001 = false;
                    }
                }

                var59.printStackTrace();
            }

            var10000 = xml;
            String var57 = "";

            label215: {
                label214: {
                    label405: {
                        try {
                            var60 = var10000.contains("<song:quality>");
                        } catch (Exception var15) {
                            var10001 = false;
                            break label405;
                        }

                        if (!var60) {
                            break label215;
                        }

                        try {
                            var10000 = xml;
                            var61 = xml;
                            var10002 = xml.indexOf("<song:quality>");
                        } catch (Exception var14) {
                            var10001 = false;
                            break label405;
                        }

                        var8 = var10002;

                        try {
                            var10000 = var10000.substring(var8, var61.indexOf("</song:quality>")).replace("<song:quality>", "");
                            break label214;
                        } catch (Exception var13) {
                            var10001 = false;
                        }
                    }

                    var57 = "0";
                    break label215;
                }

                var57 = var10000;
            }

            String var9 = "";
            if (xml.contains("<song:skiplimit>")) {
                int var58 = xml.indexOf("<song:skiplimit>");
                var9 = xml.substring(var58, xml.indexOf("</song:skiplimit>")).replace("<song:skiplimit>", "");
            }

            String var10 = "";
            String var11 = "";
            Matcher xml1;
            if ((xml1 = Pattern.compile("<res protocolInfo=\"(.*)\" duration=\"(.*)\">(.*)</res>", 43).matcher(xml)).find()) {
                var10 = xml1.group(2);
                var11 = xml1.group(3);
            }

            var1.setTitle(var3);
            var1.setArtist(var53);
            var1.setAlbum(var54);
            var1.setAlbumArtURI(var55);
            if (!TextUtils.isEmpty(var56)) {
                try {
                    var1.setSong_id(var56);
                } catch (Exception var12) {
                    var1.setSong_id("");
                }
            }

            if (!TextUtils.isEmpty(var9)) {
                var1.setSkiplimit(Integer.parseInt(var9));
            }

            var1.setCreator(var1.getArtist());
            var1.setTypeDescription(var2);
            var1.setDuration(praseLong(var10));
            var1.setPlayUri(var11);
            int xml2;
            if ((TextUtils.isEmpty(var57) ? 0 : Integer.parseInt(var57)) == 0) {
                xml2 = 1;
            } else {
                xml2 = Integer.parseInt(var57);
            }

            var1.setQuality(xml2);
            return var1;
        }
    }

    public static synchronized MediaInfo convertQplayMeta2AlbumInfo(String jsonMetadata) {
        JSONObject var10000;
        boolean var10001;
        try {
            var10000 = new JSONObject(jsonMetadata);
        } catch (Exception var20) {
            var10001 = false;
            return null;
        }

        JSONObject var1;
        JSONObject var25 = var1 = var10000;

        MediaInfo var26;
        try {
            var26 = new MediaInfo();
        } catch (Exception var19) {
            var10001 = false;
            return null;
        }

        MediaInfo jsonMetadata1 = var26;

        boolean var23;
        try {
            var23 = var10000.has("songID");
        } catch (Exception var18) {
            var10001 = false;
            return null;
        }

        if (var23) {
            label157: {
                MediaInfo var24;
                StringBuilder var27;
                try {
                    var24 = jsonMetadata1;
                    var27 = new StringBuilder();
                } catch (Exception var6) {
                    var10001 = false;
                    break label157;
                }

                StringBuilder var10002 = var27;

                try {
                    var24.setSong_id(var27.append(var1.getLong("songID")).append("").toString());
                } catch (Exception var5) {
                    var10001 = false;
                }
            }
        }

        try {
            var23 = var1.has("duration");
        } catch (Exception var17) {
            var10001 = false;
            return null;
        }

        if (var23) {
            try {
                jsonMetadata1.setDuration(praseLong(var1.getString("duration")));
            } catch (Exception var16) {
                var10001 = false;
                return null;
            }
        }

        try {
            var23 = var1.has("title");
        } catch (Exception var15) {
            var10001 = false;
            return null;
        }

        if (var23) {
            try {
                jsonMetadata1.setTitle(var1.getString("title"));
            } catch (Exception var14) {
                var10001 = false;
                return null;
            }
        }

        try {
            var23 = var1.has("albumArtURI");
        } catch (Exception var13) {
            var10001 = false;
            return null;
        }

        if (var23) {
            try {
                jsonMetadata1.setAlbumArtURI(var1.getString("albumArtURI"));
            } catch (Exception var12) {
                var10001 = false;
                return null;
            }
        }

        try {
            var23 = var1.has("album");
        } catch (Exception var11) {
            var10001 = false;
            return null;
        }

        if (var23) {
            try {
                jsonMetadata1.setAlbum(var1.getString("album"));
            } catch (Exception var10) {
                var10001 = false;
                return null;
            }
        }

        try {
            var23 = var1.has("creator");
        } catch (Exception var9) {
            var10001 = false;
            return null;
        }

        if (var23) {
            try {
                jsonMetadata1.setArtist(var1.getString("creator"));
                jsonMetadata1.setCreator(jsonMetadata1.getArtist());
            } catch (Exception var8) {
                var10001 = false;
                return null;
            }
        }

        try {
            var23 = var1.has("trackURIs");
        } catch (Exception var7) {
            var10001 = false;
            return null;
        }

        if (var23) {
            JSONArray var28;
            try {
                var28 = var1.getJSONArray("trackURIs");
            } catch (Exception var4) {
                var10001 = false;
                return jsonMetadata1;
            }

            JSONArray var22 = var28;

            int var29;
            try {
                var29 = var28.length();
            } catch (Exception var3) {
                var10001 = false;
                return jsonMetadata1;
            }

            if (var29 > 0) {
                try {
                    jsonMetadata1.setPlayUri(var22.getString(0));
                } catch (Exception var2) {
                    var10001 = false;
                }
            }
        }

        return jsonMetadata1;
    }

    public static long fromTimeString(String s) {
        String[] split = s.split(":");
        String sec = split[2];
        if (sec.contains(".")) {
            sec = sec.substring(0, sec.indexOf("."));
        }
        return (Long.parseLong(split[0]) * 3600) +
                (Long.parseLong(split[1]) * 60) +
                (Long.parseLong(sec));
    }

    private static long praseLong(String strDuration) {
        if (strDuration != null && strDuration.trim().length() > 0) {
            return strDuration.contains(":") ? fromTimeString(strDuration) * 1000L : Long.parseLong(strDuration);
        } else {
            return 0L;
        }
    }
}

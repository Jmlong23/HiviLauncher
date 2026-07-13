package com.ljm.audiotoollib.upnpserver.entity;

import java.util.List;

public class AudioBackgroundInfo {
    String url = "";

    // 0全国电台，5和6也是电台。1qq，2spotify，3网易云，4酷狗。 其他都是ai平台
    List<String> terraceTypeList;

    // 设置类型，壁纸:0、歌词:1、视频:2、AI内容:3、音律：4
    int type = 0;


    //当type为其他时，0代表mp4格式，1代表png...
    int subType = 0;

    //当type为1, 歌词样式，可以传多个类型
    //当type为4, 音律样式，可以传多个类型
    List<String> subTypeList;

    public void setUrl(String value) {
        this.url = value;
    }

    public String getUrl() {
        return url;
    }

    public void setType(int value) {
        this.type = value;
    }

    public int getType() {
        return type;
    }

    public void setSubType(int subType) {
        this.subType = subType;
    }

    public int getSubType() {
        return subType;
    }

    public void setTerraceTypeList(List<String> value) {
        terraceTypeList = value;
    }

    public List<String> getTerraceTypeList() {
        return terraceTypeList;
    }

    public void setSubTypeList(List<String> value) {
        subTypeList = value;
    }

    public List<String> getSubTypeList() {
        return subTypeList;
    }

    @Override
    public String toString() {
        return "AudioBackgroundInfo{" +
                "subType=" + subType +
                ", terraceTypeList=" + terraceTypeList +
                ", type=" + type +
                ", url='" + url + '\'' +
                '}';
    }
}

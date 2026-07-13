package com.ljm.audiotoollib.upnpserver.entity;

import android.util.Log;

import com.google.gson.Gson;

/**
 *
 */
public class GetControlDeviceInfoEntity {

    private String MultiType;
    private String Router;
    private String Ssid;
    private String SlaveMask;
    private String Volume;
    private String Mute;
    private String Channel;
    private String SlaveList;
    private String Status;

    public GetControlDeviceInfoEntity() {

        Gson gson = new Gson();

        this.MultiType = "1";
        this.Router = "4545";
        this.Ssid = "MG100";
        this.SlaveMask = "0";
        this.Volume = "40";
        this.Mute = "0";
        this.Channel = "0";
        this.SlaveList = "{\"slaves\":\"0\", \"slave_list\":[]}";

       try {
           SWDeviceStatus status = new SWDeviceStatus();
           this.Status = gson.toJson(status);
       } catch (Exception e){
           e.printStackTrace();
       }
    }

    public void setMultiType(String value) {
        MultiType = value;
    }

    public String getMultiType() {
        return MultiType;
    }

    public String getRouter() {
        return Router;
    }

    public String getSsid() {
        return Ssid;
    }

    public String getSlaveMask() {
        return SlaveMask;
    }

    public String getVolume() {
        return Volume;
    }

    public String getMute() {
        return Mute;
    }

    public String getChannel() {
        return Channel;
    }

    public String getSlaveList() {
        return SlaveList;
    }

    public String getStatus() {
        return Status;
    }
}

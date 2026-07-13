package com.ljm.audiotoollib.upnpserver.entity;


import java.util.ArrayList;
import java.util.List;

public class SlaveBean {


    /**
     * slaves : 1
     * slave_list : [{"name":"HiVi M5A","ssid":"HiVi M5A_D08C","mask":0,"volume":19,"mute":0,"channel":0,"battery":0,"ip":"10.10.10.92","version":"4.2.7908","uuid":"uuid:FF31F0AB-B486-5EB4-92BA-30CFFF31F0AB"}]
     */

    private int slaves;
    private List<SlaveListDTO> slave_list = new ArrayList<>();

    public int getSlaves() {
        return slaves;
    }

    public void setSlaves(int slaves) {
        this.slaves = slaves;
    }

    public List<SlaveListDTO> getSlave_list() {
        return slave_list;
    }

    public void setSlave_list(List<SlaveListDTO> slave_list) {
        this.slave_list = slave_list;
    }

    public static class SlaveListDTO {
        /**
         * name : HiVi M5A
         * ssid : HiVi M5A_D08C
         * mask : 0
         * volume : 19
         * mute : 0
         * channel : 0
         * battery : 0
         * ip : 10.10.10.92
         * version : 4.2.7908
         * uuid : uuid:FF31F0AB-B486-5EB4-92BA-30CFFF31F0AB
         */

        private String name;
        private String ssid;
        private int mask;
        private int volume;
        private int mute;
        private int channel;
        private int battery;
        private String ip;
        private String version;
        private String uuid;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSsid() {
            return ssid;
        }

        public void setSsid(String ssid) {
            this.ssid = ssid;
        }

        public int getMask() {
            return mask;
        }

        public void setMask(int mask) {
            this.mask = mask;
        }

        public int getVolume() {
            return volume;
        }

        public void setVolume(int volume) {
            this.volume = volume;
        }

        public int getMute() {
            return mute;
        }

        public void setMute(int mute) {
            this.mute = mute;
        }

        public int getChannel() {
            return channel;
        }

        public void setChannel(int channel) {
            this.channel = channel;
        }

        public int getBattery() {
            return battery;
        }

        public void setBattery(int battery) {
            this.battery = battery;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }
    }
}

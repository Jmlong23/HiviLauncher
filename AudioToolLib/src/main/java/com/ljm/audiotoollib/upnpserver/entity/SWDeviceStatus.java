package com.ljm.audiotoollib.upnpserver.entity;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class SWDeviceStatus {


    /**
     * language : zh_cn
     * ssid : SWAN MT1-mini_B56E
     * hideSSID : 0
     * firmware : 3.6.4122
     * build : release
     * project : SWAN MT1-mini
     * priv_prj : SWAN MT1-mini
     * Release : 20170222
     * group : 0
     * expired : 0
     * internet : 1
     * uuid : FF31F012FA4780EB4A730286
     * MAC : 00-22-6C-02-B5-6E
     * date : 2024:08:28
     * time : 06:53:25
     * netstat : 2
     * essid : 54502D4C494E4B5F4C504B
     * apcli0 : 192.168.1.140
     * eth2 : 0.0.0.0
     * hardware : ANDROID_AUDIO
     * VersionUpdate : 0
     * NewVer : 0
     * mcu_ver : 0
     * mcu_ver_new : 0
     * ra0 : 10.10.10.254
     * temp_uuid : 360B7EB8D10E0518
     * cap1 : 0x104200
     * capability : enable
     * languages : 0x6
     * dsp_ver :
     * streams_all : 0x7ffffffe
     * streams : 0x7ffffffe
     * region : unknown
     * external : 0x0
     * preset_key : 6
     * plm_support : 0x0
     * WifiChannel : 6
     * RSSI : -43
     * battery : 0
     * battery_percent : 0
     * securemode : 1
     * auth : WPAPSKWPA2PSK
     * encry : AES
     * psk : 12345678
     * upnp_version : 1004
     * upnp_uuid : uuid:FF31F012-FA47-80EB-4A73-0286FF31F012
     * uart_pass_port : 8899
     * communication_port : 8819
     * web_firmware_update_hide : 0
     * privacy_mode : 0
     * user1 : 311:524
     * user2 : 5951:6291
     * DeviceName : SWAN MT1-mini
     * GroupName : SWAN MT1-mini
     */

    private String language;
    private String ssid;
    private String hideSSID;
    private String firmware;
    private String build;
    private String project;
    private String priv_prj;
    private String Release;
    private String group;
    private String expired;
    private String internet;
    private String uuid;
    private String MAC;
    private String date;
    private String time;
    private String netstat;
    private String essid;
    private String apcli0;
    private String eth2;
    private String hardware;
    private String VersionUpdate;
    private String NewVer;
    private String mcu_ver;
    private String mcu_ver_new;
    private String ra0;
    private String temp_uuid;
    private String cap1;
    private String capability;
    private String languages;
    private String dsp_ver;
    private String streams_all;
    private String streams;
    private String region;
    private String external;
    private String preset_key;
    private String plm_support;
    private String WifiChannel;
    private String RSSI;
    private String battery;
    private String battery_percent;
    private String securemode;
    private String auth;
    private String encry;
    private String psk;
    private String upnp_version;
    private String upnp_uuid;
    private String uart_pass_port;
    private String communication_port;
    private String web_firmware_update_hide;
    private String privacy_mode;
    private String user1;
    private String user2;
    private String DeviceName;
    private String GroupName;

    public static final int AND_HARDWARE_PORT = 8080;

    public static  String deviceName = "MG100";
    public static String companyWebsite = "www.hivi.com";
    public static String macAddress = "";
    public static String ipAddress = "";
    public static String deviceAbility = "enable";

    public static void setIpAddress(String value) {
        ipAddress = value;
    }

    public static void setMacAddress(String value) {
        macAddress = value;
    }

    public static void updateDeviceName(String name){
        deviceName = name;
    }

    public static void updateDeviceAbility(boolean enable) {
//        deviceAbility = enable ? "enable" : "disable";
        deviceAbility = "enable";
    }

    public static UUID getUUID() {
        StringBuilder builder = new StringBuilder();
        builder.append(companyWebsite);
        builder.append(android.os.Build.MODEL);
        builder.append(android.os.Build.MANUFACTURER);
        builder.append(macAddress);

        byte[] hash = builder.toString().getBytes();
        try {
            hash = MessageDigest.getInstance("MD5").digest(builder.toString().getBytes());
        } catch (Exception e) {

        }
        UUID uuid = new UUID(new BigInteger(-1, hash).longValue(), "MediaPlayer".hashCode());
        return uuid;
    }


    public SWDeviceStatus() {
        final SimpleDateFormat hourTime = new SimpleDateFormat("HH:mm:ss");
        final SimpleDateFormat yearTime = new SimpleDateFormat("yyyy:MM:dd");

        String yearStr = yearTime.format(new Date());
        String hourStr = hourTime.format(new Date());

        this.setLanguage("zh_cn");
        this.setSsid(deviceName + "_ANDROID_MIC");
        this.setHideSSID("0");
        this.setFirmware("1.3.19");
        this.setBuild("debug");
        this.setProject(deviceName);
        this.setPriv_prj(deviceName);
        this.setRelease("20220301D");
        this.setGroup("0");
        this.setExpired("0");
        this.setInternet("1");
        this.setUuid(getUUID().toString());
        this.setMAC(macAddress);
        this.setDate(yearStr);
        this.setTime(hourStr);
        this.setNetstat("2");
        this.setEssid("7869616F78696E6768616F2D3547");
        this.setApcli0(ipAddress);
        this.setEth2("0.0.0.0");
        this.setHardware("ANDROID_AUDIO");
        this.setVersionUpdate("0");
        this.setNewVer("0");
        this.setMcu_ver("0");
        this.setMcu_ver_new("0");
        this.setRa0("10.10.10.254");
        this.setTemp_uuid("9850AA50102A0024");
        this.setCap1("0x104200");
        this.setCapability(deviceAbility);
        this.setLanguages("0x6");
        this.setDsp_ver("");
        this.setStreams_all("0x7ffffffe");
        this.setStreams("0x7f9833fe");
        this.setRegion("unknown");
        this.setExternal("0x0");
        this.setPreset_key("6");
        this.setPlm_support("0x0");
        this.setWifiChannel("1");
        this.setRSSI("-57");
        this.setBattery("0");
        this.setBattery_percent("0");
        this.setSecuremode("1");
        this.setAuth("WPAPSKWPA2PSK");
        this.setEncry("AES");
        this.setPsk("12345678");
        this.setUpnp_version("1000");
        this.setUpnp_uuid("uuid:" + getUUID().toString());
        this.setUart_pass_port("8899");
        this.setCommunication_port(String.valueOf(AND_HARDWARE_PORT));
        this.setWeb_firmware_update_hide("0");
        this.setPrivacy_mode("0");
        this.setUser1("278:524");
        this.setUser2("5947:6291");
        this.setDeviceName(deviceName);
        this.setGroupName(deviceName);
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getHideSSID() {
        return hideSSID;
    }

    public void setHideSSID(String hideSSID) {
        this.hideSSID = hideSSID;
    }

    public String getFirmware() {
        return firmware;
    }

    public void setFirmware(String firmware) {
        this.firmware = firmware;
    }

    public String getBuild() {
        return build;
    }

    public void setBuild(String build) {
        this.build = build;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getPriv_prj() {
        return priv_prj;
    }

    public void setPriv_prj(String priv_prj) {
        this.priv_prj = priv_prj;
    }

    public String getRelease() {
        return Release;
    }

    public void setRelease(String Release) {
        this.Release = Release;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getExpired() {
        return expired;
    }

    public void setExpired(String expired) {
        this.expired = expired;
    }

    public String getInternet() {
        return internet;
    }

    public void setInternet(String internet) {
        this.internet = internet;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getMAC() {
        return MAC;
    }

    public void setMAC(String MAC) {
        this.MAC = MAC;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getNetstat() {
        return netstat;
    }

    public void setNetstat(String netstat) {
        this.netstat = netstat;
    }

    public String getEssid() {
        return essid;
    }

    public void setEssid(String essid) {
        this.essid = essid;
    }

    public String getApcli0() {
        return apcli0;
    }

    public void setApcli0(String apcli0) {
        this.apcli0 = apcli0;
    }

    public String getEth2() {
        return eth2;
    }

    public void setEth2(String eth2) {
        this.eth2 = eth2;
    }

    public String getHardware() {
        return hardware;
    }

    public void setHardware(String hardware) {
        this.hardware = hardware;
    }

    public String getVersionUpdate() {
        return VersionUpdate;
    }

    public void setVersionUpdate(String VersionUpdate) {
        this.VersionUpdate = VersionUpdate;
    }

    public String getNewVer() {
        return NewVer;
    }

    public void setNewVer(String NewVer) {
        this.NewVer = NewVer;
    }

    public String getMcu_ver() {
        return mcu_ver;
    }

    public void setMcu_ver(String mcu_ver) {
        this.mcu_ver = mcu_ver;
    }

    public String getMcu_ver_new() {
        return mcu_ver_new;
    }

    public void setMcu_ver_new(String mcu_ver_new) {
        this.mcu_ver_new = mcu_ver_new;
    }

    public String getRa0() {
        return ra0;
    }

    public void setRa0(String ra0) {
        this.ra0 = ra0;
    }

    public String getTemp_uuid() {
        return temp_uuid;
    }

    public void setTemp_uuid(String temp_uuid) {
        this.temp_uuid = temp_uuid;
    }

    public String getCap1() {
        return cap1;
    }

    public void setCap1(String cap1) {
        this.cap1 = cap1;
    }

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public String getDsp_ver() {
        return dsp_ver;
    }

    public void setDsp_ver(String dsp_ver) {
        this.dsp_ver = dsp_ver;
    }

    public String getStreams_all() {
        return streams_all;
    }

    public void setStreams_all(String streams_all) {
        this.streams_all = streams_all;
    }

    public String getStreams() {
        return streams;
    }

    public void setStreams(String streams) {
        this.streams = streams;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getExternal() {
        return external;
    }

    public void setExternal(String external) {
        this.external = external;
    }

    public String getPreset_key() {
        return preset_key;
    }

    public void setPreset_key(String preset_key) {
        this.preset_key = preset_key;
    }

    public String getPlm_support() {
        return plm_support;
    }

    public void setPlm_support(String plm_support) {
        this.plm_support = plm_support;
    }

    public String getWifiChannel() {
        return WifiChannel;
    }

    public void setWifiChannel(String WifiChannel) {
        this.WifiChannel = WifiChannel;
    }

    public String getRSSI() {
        return RSSI;
    }

    public void setRSSI(String RSSI) {
        this.RSSI = RSSI;
    }

    public String getBattery() {
        return battery;
    }

    public void setBattery(String battery) {
        this.battery = battery;
    }

    public String getBattery_percent() {
        return battery_percent;
    }

    public void setBattery_percent(String battery_percent) {
        this.battery_percent = battery_percent;
    }

    public String getSecuremode() {
        return securemode;
    }

    public void setSecuremode(String securemode) {
        this.securemode = securemode;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getEncry() {
        return encry;
    }

    public void setEncry(String encry) {
        this.encry = encry;
    }

    public String getPsk() {
        return psk;
    }

    public void setPsk(String psk) {
        this.psk = psk;
    }

    public String getUpnp_version() {
        return upnp_version;
    }

    public void setUpnp_version(String upnp_version) {
        this.upnp_version = upnp_version;
    }

    public String getUpnp_uuid() {
        return upnp_uuid;
    }

    public void setUpnp_uuid(String upnp_uuid) {
        this.upnp_uuid = upnp_uuid;
    }

    public String getUart_pass_port() {
        return uart_pass_port;
    }

    public void setUart_pass_port(String uart_pass_port) {
        this.uart_pass_port = uart_pass_port;
    }

    public String getCommunication_port() {
        return communication_port;
    }

    public void setCommunication_port(String communication_port) {
        this.communication_port = communication_port;
    }

    public String getWeb_firmware_update_hide() {
        return web_firmware_update_hide;
    }

    public void setWeb_firmware_update_hide(String web_firmware_update_hide) {
        this.web_firmware_update_hide = web_firmware_update_hide;
    }

    public String getPrivacy_mode() {
        return privacy_mode;
    }

    public void setPrivacy_mode(String privacy_mode) {
        this.privacy_mode = privacy_mode;
    }

    public String getUser1() {
        return user1;
    }

    public void setUser1(String user1) {
        this.user1 = user1;
    }

    public String getUser2() {
        return user2;
    }

    public void setUser2(String user2) {
        this.user2 = user2;
    }

    public String getDeviceName() {
        return DeviceName;
    }

    public void setDeviceName(String DeviceName) {
        this.DeviceName = DeviceName;
    }

    public String getGroupName() {
        return GroupName;
    }

    public void setGroupName(String GroupName) {
        this.GroupName = GroupName;
    }
}

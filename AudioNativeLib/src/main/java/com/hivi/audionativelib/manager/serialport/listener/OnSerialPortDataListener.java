package com.hivi.audionativelib.manager.serialport.listener;

public interface OnSerialPortDataListener {

    void onDataReceived(String data);

    void onDataSent(byte[] bytes);
}

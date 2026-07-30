package com.hivi.audionativelib.manager.serialport.listener;

import java.io.File;

/**
 * 打开串口监听
 */
public interface OnOpenSerialPortListener {

    void onSuccess(File device);
    void onFail(File device, Status status);

    enum Status {
        NO_READ_WRITE_PERMISSION, // 无读写权限
        OPEN_FAIL // 打开失败
    }
}

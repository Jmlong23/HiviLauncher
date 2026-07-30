package com.hivi.audionativelib.manager.serialport;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import com.hivi.audionativelib.NativeLog;


import com.hivi.audionativelib.manager.serialport.listener.OnOpenSerialPortListener;
import com.hivi.audionativelib.manager.serialport.listener.OnSerialPortDataListener;
import com.hivi.audionativelib.manager.serialport.thread.SerialPortReadThread;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


public class SerialPortManager extends SerialPort {
     private static final String TAG = SerialPortManager.class.getSimpleName();
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;
    private FileDescriptor mFd;
    private OnOpenSerialPortListener mOnOpenSerialPortListener;
    private OnSerialPortDataListener mOnSerialPortDataListener;

    private HandlerThread mSendingHandlerThread;
    private Handler mSendingHandler;
    private SerialPortReadThread mSerialPortReadThread;


    public boolean openSerialPort(File device, int baudRate) {

        NativeLog.i(TAG, "SerialPortManager openSerialPort: " + String.format("打开串口 %s  波特率 %s", device.getPath(), baudRate));

        if (!device.canRead() || !device.canWrite()) {
            boolean chmod777 = chmod777(device);
            if (!chmod777) {
                NativeLog.i(TAG, "SerialPortManager openSerialPort: 没有读写权限");
                if (null != mOnOpenSerialPortListener) {
                    mOnOpenSerialPortListener.onFail(device, OnOpenSerialPortListener.Status.NO_READ_WRITE_PERMISSION);
                }
                return false;
            }
        }

        try {
            mFd = openNative(device.getAbsolutePath(), baudRate, 0);
            mFileInputStream = new FileInputStream(mFd);
            mFileOutputStream = new FileOutputStream(mFd);
            NativeLog.i(TAG, "SerialPortManager openSerialPort: 串口已经打开 " + mFd);
            startSendThread();
            startReadThread();
            if (null != mOnOpenSerialPortListener) {
                mOnOpenSerialPortListener.onSuccess(device);
            }
            return true; // 【3】
        } catch (Exception e) {
            e.printStackTrace();
            if (null != mOnOpenSerialPortListener) {
                mOnOpenSerialPortListener.onFail(device, OnOpenSerialPortListener.Status.OPEN_FAIL);
            }
        }
        return false;
    }


    public void closeSerialPort() {
        if (null != mFd) {
            closeNative();
            mFd = null;
        }
        stopSendThread();
        stopReadThread();

        if (null != mFileInputStream) {
            try {
                mFileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mFileInputStream = null;
        }

        if (null != mFileOutputStream) {
            try {
                mFileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mFileOutputStream = null;
        }
        mOnOpenSerialPortListener = null;
        mOnSerialPortDataListener = null;
    }


    public SerialPortManager setOnOpenSerialPortListener(OnOpenSerialPortListener listener) {
        mOnOpenSerialPortListener = listener;
        return this;
    }


    public SerialPortManager setOnSerialPortDataListener(OnSerialPortDataListener listener) {
        mOnSerialPortDataListener = listener;
        return this;
    }


    private void startSendThread() {
        mSendingHandlerThread = new HandlerThread("mSendingHandlerThread");
        mSendingHandlerThread.start();
        mSendingHandler = new Handler(mSendingHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                byte[] sendBytes = (byte[]) msg.obj;

                if (null != mFileOutputStream && null != sendBytes && 0 < sendBytes.length) {
                    try {
                        mFileOutputStream.write(sendBytes);
                        if (null != mOnSerialPortDataListener) {
                            mOnSerialPortDataListener.onDataSent(sendBytes); // 【发送 1】
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }


    private void stopSendThread() {
        mSendingHandler = null;
        if (null != mSendingHandlerThread) {
            mSendingHandlerThread.interrupt();
            mSendingHandlerThread.quit();
            mSendingHandlerThread = null;
        }
    }


    private void startReadThread() {
        mSerialPortReadThread = new SerialPortReadThread(mFileInputStream) {
            @Override
            public void onDataReceived(String data) {
                if (null != mOnSerialPortDataListener) {
                    mOnSerialPortDataListener.onDataReceived(data);
                }
            }
        };
        mSerialPortReadThread.start();
    }


    private void stopReadThread() {
        if (null != mSerialPortReadThread) {
            mSerialPortReadThread.release();
        }
    }

    public boolean sendBytes(byte[] sendBytes) {
        if (null != mFd && null != mFileInputStream && null != mFileOutputStream) {
            if (null != mSendingHandler) {
                Message message = Message.obtain();
                message.obj = sendBytes;
                return mSendingHandler.sendMessage(message);
            }
        }
        return false;
    }

    public FileDescriptor openNative(String path, int baudRate, int flags) {
        return JNIopenNative(path,baudRate,flags);
    }

    public void closeNative() {
        JNIcloseNative();
    }

    protected native FileDescriptor JNIopenNative(String path, int baudRate, int flags); // 打开串口-native函数

    protected native void JNIcloseNative();
}

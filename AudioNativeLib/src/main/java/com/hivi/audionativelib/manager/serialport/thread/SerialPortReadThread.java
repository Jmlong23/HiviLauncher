package com.hivi.audionativelib.manager.serialport.thread;

import com.hivi.audionativelib.NativeLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public abstract class SerialPortReadThread extends Thread {

    public abstract void onDataReceived(String bytes);

    private static final String TAG = SerialPortReadThread.class.getSimpleName();
    private InputStream mInputStream;

    public SerialPortReadThread(InputStream inputStream) {
        mInputStream = inputStream;
    }



    @Override
    public void run() {
        super.run();
        byte[] readBuffer = new byte[512];
        ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
        while (!isInterrupted()) {
            try {
                if (null == mInputStream) {
                    return;
                }

                int size = mInputStream.read(readBuffer);
                if (size < 0) {
                    return;
                }
                if (size == 0) {
                    continue;
                }

                for (int i = 0; i < size; i++) {
                    byte b = readBuffer[i];
                    if (b == '\r') {
                        continue;
                    }
                    frameBuffer.write(b);

                    // 兼容两种常见分隔：'&'（协议尾）和 '\n'
                    if (b == '&' || b == '\n') {
                        dispatchFrame(frameBuffer.toByteArray());
                        frameBuffer.reset();
                    }
                }

                // 防止异常数据导致缓存无限增长
                if (frameBuffer.size() > 4096) {
                    dispatchFrame(frameBuffer.toByteArray());
                    frameBuffer.reset();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private void dispatchFrame(byte[] frameBytes) {
        if (frameBytes == null || frameBytes.length == 0) {
            return;
        }
        String line = decodeFrame(frameBytes);
        NativeLog.i(TAG, " decodeData line = " + line);
        onDataReceived(line);
    }

    private String decodeFrame(byte[] frameBytes) {
        String utf8 = decodeUtf8Strict(frameBytes);
        if (utf8 != null) {
            return utf8;
        }
        return new String(frameBytes, Charset.forName("GBK"));
    }

    private String decodeUtf8Strict(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    @Override
    public synchronized void start() {
        super.start();
    }


    public void release() {
        interrupt();

        if (null != mInputStream) {
            try {
                mInputStream.close();
                mInputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

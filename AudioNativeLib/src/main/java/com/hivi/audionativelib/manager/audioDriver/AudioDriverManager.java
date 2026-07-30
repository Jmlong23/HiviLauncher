package com.hivi.audionativelib.manager.audioDriver;

import com.hivi.audionativelib.NativeLog;

import java.io.IOException;
import java.io.OutputStream;

public class AudioDriverManager {


    private final String TAG = "AudioDriverManager";
    private static final String port = "/dev/misc_ec0902";
    CustomThread thread = null;
    private OnDriverDataListener dataListener;
    int fd = -1;


    class CustomThread extends Thread {
        @Override
        public void run() {
            while (true) {
                byte[] bytes = audioDriverRead(fd);
                if (bytes == null) {
                    continue;
                }
                String str = new String(bytes);
                dataListener.onReceived(str);
            }
        }
    }

    public void setDataListener(OnDriverDataListener dataListener) {
        this.dataListener = dataListener;
    }

    private boolean chmod777(String ttyName) {
        try {
            Process su = Runtime.getRuntime().exec("su");
            String cmd = "chmod 777 " + ttyName + "\n" + "exit\n";
            OutputStream out = su.getOutputStream();
            out.write(cmd.getBytes());
            out.flush();
            if (0 == su.waitFor()) {
                return true;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }


    public void openAudioDriver() {
        boolean res = chmod777(port);
        fd = audioDriverOpen("/dev/misc_ec0902");
        NativeLog.e(TAG, "result: " + fd + " chmod: " + res);
        if(fd > 0) {
            thread = new CustomThread();
            thread.start();
        }
    }

    public void sendAudioDriverCmd(String cmd){
        NativeLog.i(TAG, "sendMg100DriverCmd cmd: " + cmd);
        audioDriverWrite(fd, cmd);
    }

    /**
     * 打开ec0902编码器设备
     *
     * @param path     设备名称
     * @return 设备的文件描述符 成功返回 >0; 失败返回-1
     */
    public native int audioDriverOpen(String path);

    /**
     * 关闭设备
     *
     * @return 成功返回0; 失败返回-1
     */
    public native int audioDriverClose(int fd);

    /**
     * 读取数据
     *
     * @return 成功返回数据; 失败返回空
     */
    public native byte[] audioDriverRead(int fd);

    /**
     * 发送数据
     *
     * @param message 要发送的数据
     * @return 成功返回发送的字节大小; 失败返回-1
     */
    public native int audioDriverWrite(int fd, String message);

}

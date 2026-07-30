package com.hivi.audionativelib.manager.serialport;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;


public class SerialPort {

    private static final String TAG = SerialPort.class.getSimpleName();
    boolean chmod777(File file) {
        if (null == file || !file.exists()) {
            return false;
        }
        try {
            Process su = Runtime.getRuntime().exec("su");
            String cmd = "chmod 777 " + file.getAbsolutePath() + "\n" + "exit\n";
            OutputStream out = su.getOutputStream();
            out.write(cmd.getBytes());
            out.flush();
            if (0 == su.waitFor() && file.canRead() && file.canWrite() && file.canExecute()) {
                return true;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

}

package com.hivi.audionativelib.manager.serialport;

import java.io.File;
import java.io.Serializable;

public class SerialDevice implements Serializable {

    private static final String TAG = SerialDevice.class.getSimpleName();

    private String name;
    private String root;
    private File file;

    public SerialDevice(String name, String root, File file) {
        this.name = name;
        this.root = root;
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File path) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "Device{" +
                "name='" + name + '\'' +
                ", root='" + root + '\'' +
                ", file=" + file +
                '}';
    }
}

package com.hivi.audionativelib.manager.serialport;

import com.hivi.audionativelib.NativeLog;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;

public class SerialPortFinder {

    private static final String DRIVERS_PATH = "/proc/tty/drivers";
    private static final String SERIAL_FIELD = "serial";

    public SerialPortFinder() {
        File file = new File(DRIVERS_PATH);
        boolean b = file.canRead();
        NativeLog.i(T.TAG, "SerialPortFinder: file.canRead() = " + b);
    }

    private ArrayList<Driver> getDrivers() throws IOException {
        ArrayList<Driver> drivers = new ArrayList<>();
        LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(DRIVERS_PATH));
        String readLine;
        while ((readLine = lineNumberReader.readLine()) != null) {
            String driverName = readLine.substring(0, 0x15).trim();
            String[] fields = readLine.split(" +");

            NativeLog.d(T.TAG, "SerialPortFinder getDrivers() driverName:" + driverName);
            if ((fields.length >= 5) && (fields[fields.length - 1].equals(SERIAL_FIELD))) {
                NativeLog.d(T.TAG, "SerialPortFinder getDrivers() 找到了新串口驱动是:" + driverName + " 此串口系列名是:" + fields[fields.length - 4]);
                drivers.add(new Driver(driverName, fields[fields.length - 4]));
            }
        }
        return drivers;
    }

    public ArrayList<SerialDevice> getDevices() {
        ArrayList<SerialDevice> devices = new ArrayList<>();
        try {
            ArrayList<Driver> drivers = getDrivers();
            for (Driver driver : drivers) {
                String driverName = driver.getName();
                ArrayList<File> driverDevices = driver.getDevices();
                for (File file : driverDevices) {
                    String devicesName = file.getName();
                    devices.add(new SerialDevice(devicesName, driverName, file));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return devices;
    }
}

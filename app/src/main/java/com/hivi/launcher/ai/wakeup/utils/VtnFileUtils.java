package com.hivi.launcher.ai.wakeup.utils;

import com.hivi.launcher.utils.log.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * VTN 自定义唤醒词资源的读写工具。
 */
public final class VtnFileUtils {
    private static final String TAG = "VtnFileUtils";

    private VtnFileUtils() {
    }

    /**
     * 读取文件内容为字节数组，失败返回 null。
     */
    public static byte[] readFileToBytes(File file) {
        if (file == null || !file.isFile()) {
            AppLog.w(TAG, "readFileToBytes: file is not a valid file");
            return null;
        }
        long fileSize = file.length();
        if (fileSize <= 0L || fileSize > Integer.MAX_VALUE) {
            AppLog.w(TAG, "readFileToBytes: unexpected file size " + fileSize);
            return null;
        }
        byte[] buffer = new byte[(int) fileSize];
        try (InputStream input = new FileInputStream(file)) {
            int read = input.read(buffer);
            if (read != fileSize) {
                AppLog.w(TAG, "readFileToBytes: incomplete read, expected=" + fileSize
                        + ", actual=" + read);
                return null;
            }
            return buffer;
        } catch (IOException exception) {
            AppLog.e(TAG, "readFileToBytes failed", exception);
            return null;
        }
    }

    /**
     * 将字节数组的前 {@code dataSize} 字节以覆盖模式写入文件。
     */
    public static boolean writeBytesToFile(File file, byte[] data, int dataSize) {
        if (file == null || data == null || dataSize <= 0 || dataSize > data.length) {
            AppLog.w(TAG, "writeBytesToFile: invalid arguments");
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            AppLog.w(TAG, "writeBytesToFile: unable to create " + parent.getAbsolutePath());
            return false;
        }
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(data, 0, dataSize);
            output.flush();
            return true;
        } catch (IOException exception) {
            AppLog.e(TAG, "writeBytesToFile failed", exception);
            return false;
        }
    }
}

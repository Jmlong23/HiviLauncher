package com.hivi.launcher.update;

import android.text.TextUtils;

/**
 * Version information returned by the update service for the launcher package.
 */
public final class SystemUpdateInfo {
    private final String mCurrentVersionName;
    private final long mCurrentVersionCode;
    private final String mLatestVersionName;
    private final long mLatestVersionCode;
    private final String mDownloadUrl;

    public SystemUpdateInfo(String currentVersionName, long currentVersionCode,
            String latestVersionName, long latestVersionCode, String downloadUrl) {
        mCurrentVersionName = currentVersionName;
        mCurrentVersionCode = currentVersionCode;
        mLatestVersionName = latestVersionName;
        mLatestVersionCode = latestVersionCode;
        mDownloadUrl = downloadUrl;
    }

    public static SystemUpdateInfo currentVersion(String currentVersionName,
            long currentVersionCode) {
        return new SystemUpdateInfo(currentVersionName, currentVersionCode,
                currentVersionName, currentVersionCode, "");
    }

    public String getCurrentVersionName() {
        return mCurrentVersionName;
    }

    public long getCurrentVersionCode() {
        return mCurrentVersionCode;
    }

    public String getLatestVersionName() {
        return mLatestVersionName;
    }

    public long getLatestVersionCode() {
        return mLatestVersionCode;
    }

    public String getDownloadUrl() {
        return mDownloadUrl;
    }

    public boolean isUpdateAvailable() {
        return !TextUtils.isEmpty(mDownloadUrl)
                && compareVersionNames(mLatestVersionName, mCurrentVersionName) > 0;
    }

    private static int compareVersionNames(String first, String second) {
        String[] firstParts = first == null ? new String[0] : first.split("[^0-9]+");
        String[] secondParts = second == null ? new String[0] : second.split("[^0-9]+");
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < firstParts.length || secondIndex < secondParts.length) {
            long firstValue = nextVersionComponent(firstParts, firstIndex);
            long secondValue = nextVersionComponent(secondParts, secondIndex);
            firstIndex = nextComponentIndex(firstParts, firstIndex);
            secondIndex = nextComponentIndex(secondParts, secondIndex);
            if (firstValue != secondValue) {
                return firstValue > secondValue ? 1 : -1;
            }
        }
        return 0;
    }

    private static long nextVersionComponent(String[] parts, int index) {
        while (index < parts.length && TextUtils.isEmpty(parts[index])) {
            index++;
        }
        if (index >= parts.length) {
            return 0L;
        }
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static int nextComponentIndex(String[] parts, int index) {
        while (index < parts.length && TextUtils.isEmpty(parts[index])) {
            index++;
        }
        return index < parts.length ? index + 1 : index;
    }
}

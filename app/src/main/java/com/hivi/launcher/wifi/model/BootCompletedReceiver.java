package com.hivi.launcher.wifi.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Runs the boot auto-play sweep as soon as the system delivers the boot broadcast —
 * at the same moment the music apps' own BOOT_COMPLETED receivers start them, so the
 * force-stop lands before they can resume playback. See {@link BootAutoPlaySuppressor}.
 */
public final class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        BootAutoPlaySuppressor.getInstance().onBootCompleted(context);
    }
}

package com.hivi.audionativelib.manager.audioAlsa;

public interface AudioAlsaRecorderListener {
    void onWakePcmRead(short[] pcm, int frameSize);

    void onUploadPcmRead(short[] pcm, int frameSize);
}

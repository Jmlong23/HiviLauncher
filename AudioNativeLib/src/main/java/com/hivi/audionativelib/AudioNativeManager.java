package com.hivi.audionativelib;


import android.annotation.SuppressLint;

import com.hivi.audionativelib.manager.audioAlsa.AudioAlsaRecorder;
import com.hivi.audionativelib.manager.audioDriver.AudioDriverManager;
import com.hivi.audionativelib.manager.himusic.HiMisicManager;
import com.hivi.audionativelib.manager.serialport.SerialPortFinder;
import com.hivi.audionativelib.manager.serialport.SerialPortManager;

@SuppressLint("UnsafeDynamicallyLoadedCode")
public class AudioNativeManager {

    static {
        System.loadLibrary("hivinative");
    }

    static private AudioNativeManager _toolManager;

    private final SerialPortManager serialPortManager;
    private final SerialPortFinder serialPortFinder;
    private final AudioDriverManager audioDriverManager;

    private final AudioAlsaRecorder audioAlsaRecorder;

    private final HiMisicManager hiMisicManager;


    public static AudioNativeManager instance() {
        if (_toolManager == null) {
            _toolManager = new AudioNativeManager();
        }
        return _toolManager;
    }

    AudioNativeManager() {
        serialPortManager = new SerialPortManager();
        serialPortFinder = new SerialPortFinder();
        audioDriverManager = new AudioDriverManager();
        audioAlsaRecorder = new AudioAlsaRecorder();
        hiMisicManager = new HiMisicManager();
    }

    public SerialPortManager getSerialPortManager() {
        return serialPortManager;
    }

    public SerialPortFinder getSerialPortFinder() {
        return serialPortFinder;
    }

    public AudioDriverManager getAudioDriverManager() {
        return audioDriverManager;
    }

    public AudioAlsaRecorder getAudioAlsaRecorder() {
        return audioAlsaRecorder;
    }

    public HiMisicManager getHiMisicManager() { return hiMisicManager; }
}
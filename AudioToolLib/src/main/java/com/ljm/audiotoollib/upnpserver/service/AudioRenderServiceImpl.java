package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Channel;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlException;
import com.ljm.audiotoollib.upnpserver.entity.AbstractRenderingControl;

public class AudioRenderServiceImpl extends AbstractRenderingControl {
    private static final String TAG = "AudioRenderServiceImpl";
    private static final Channel[] mMasterChannel = new Channel[]{Channel.Master};
    private final RenderControlManager renderControlManager;

    public AudioRenderServiceImpl(LastChange lastChange, RenderControlManager renderControlManager) {
        super(lastChange);
        this.renderControlManager = renderControlManager;
    }

    @Override
    public void setMute(UnsignedIntegerFourBytes instanceId, String channelName, boolean desiredMute) {
        Log.e("testdlna","desiredMute" + desiredMute);
        renderControlManager.getAudioControl(instanceId).setMute(channelName, desiredMute);
    }

    @Override
    public boolean getMute(UnsignedIntegerFourBytes instanceId, String channelName) {
        return renderControlManager.getAudioControl(instanceId).getMute(channelName);
    }

    @Override
    public void setVolume(UnsignedIntegerFourBytes instanceId, String channelName, UnsignedIntegerTwoBytes desiredVolume) {
        renderControlManager.getAudioControl(instanceId).setVolume(channelName, desiredVolume);
    }

    @Override
    public UnsignedIntegerTwoBytes getVolume(UnsignedIntegerFourBytes instanceId, String channelName) {
        return renderControlManager.getAudioControl(instanceId).getVolume(channelName);
    }

    @Override
    public String getAudioMode(UnsignedIntegerFourBytes instanceId) throws RenderingControlException {
        return renderControlManager.getAudioControl(instanceId).getAudioMode();
    }

    @Override
    public void setAudioMode(UnsignedIntegerFourBytes instanceId, String desiredAudioMode) throws RenderingControlException {
        renderControlManager.getAudioControl(instanceId).setAudioMode(desiredAudioMode);
    }

    @Override
    protected Channel[] getCurrentChannels() {
        return mMasterChannel;
    }

    @Override
    public void setExtraInfo(UnsignedIntegerFourBytes instanceId, String extraInfo) throws RenderingControlException {
        Log.i(TAG,"setExtraInfo extraInfo: " + extraInfo);
        renderControlManager.getAudioControl(instanceId).setExtraInfo(extraInfo);
    }

    @Override
    public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
        return renderControlManager.getAudioControlCurrentInstanceIds();
    }

    @Override
    public void setRemoteControlMode(UnsignedIntegerFourBytes instanceId, UnsignedIntegerTwoBytes controlMode) {
        renderControlManager.getAudioControl(instanceId).setRemoteControlMode(controlMode);
    }

    @Override
    public void setAudioBackground(UnsignedIntegerFourBytes instanceId, String audioContext) {
        Log.e(TAG,"setAudioBackground audioContext: " + audioContext);
        renderControlManager.getAudioControl(instanceId).setAudioBackground(audioContext);
    }

    @Override
    public void selectAudioBackground(UnsignedIntegerFourBytes instanceId, String audioContext) throws RenderingControlException {
        Log.e(TAG,"selectAudioBackground audioContext: " + audioContext);
        renderControlManager.getAudioControl(instanceId).selectAudioBackground(audioContext);
    }

    @Override
    public void cancelAudioBackground(UnsignedIntegerFourBytes instanceId) throws RenderingControlException {
        renderControlManager.getAudioControl(instanceId).cancelAudioBackground();
    }
}

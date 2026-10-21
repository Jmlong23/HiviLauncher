package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Channel;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlException;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlVariable;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.entity.AbstractRenderingControl;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.EventedValue;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.ChannelVolume;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.ChannelMute;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioRenderServiceImpl extends AbstractRenderingControl {
    private static final String TAG = "AudioRenderServiceImpl";
    private static final Channel[] mMasterChannel = new Channel[]{Channel.Master};
    private final RenderControlManager renderControlManager;
    private final RoleController roleController;
    private final RoleController.OnRoleChangedListener roleChangedListener;
    private final WallpaperController wallpaperController;
    private final WallpaperController.OnWallpaperChangedListener wallpaperChangedListener;
    private final Consumer<Runnable> eventDispatcher;
    private final StateEventPublisher statePublisher = new StateEventPublisher();
    private volatile String reportedRoute;
    private volatile boolean closed;

    public AudioRenderServiceImpl(LastChange lastChange, RenderControlManager renderControlManager,
                                  RoleController roleController, Consumer<Runnable> eventDispatcher) {
        this(lastChange, renderControlManager, roleController, WallpaperController.getInstance(), eventDispatcher);
    }

    public AudioRenderServiceImpl(LastChange lastChange, RenderControlManager renderControlManager,
                                  RoleController roleController, WallpaperController wallpaperController,
                                  Consumer<Runnable> eventDispatcher) {
        super(lastChange);
        this.renderControlManager = renderControlManager;
        this.roleController = roleController;
        this.eventDispatcher = eventDispatcher;
        roleChangedListener = roleId -> eventDispatcher.accept(() -> publishRoleChange(roleId));
        roleController.setOnRoleChangedListener(roleChangedListener);
        this.wallpaperController = wallpaperController;
        wallpaperChangedListener = state -> eventDispatcher.accept(() -> publishWallpaperChange(state));
        wallpaperController.setOnWallpaperChangedListener(wallpaperChangedListener);
    }

    public void close() {
        closed = true;
        roleController.clearOnRoleChangedListener(roleChangedListener);
        wallpaperController.clearOnWallpaperChangedListener(wallpaperChangedListener);
    }

    private EventedValue[] wallpaperValues(WallpaperController.State state) {
        return new EventedValue[]{
                new RenderingControlVariable.WallpaperId(new UnsignedIntegerFourBytes(state.getId())),
                new RenderingControlVariable.WallpaperSource(state.getSource()),
                new RenderingControlVariable.WallpaperIds(state.getIdsJson()),
                new RenderingControlVariable.ClockId(new UnsignedIntegerFourBytes(state.getClockId())),
                new RenderingControlVariable.ClockStyle(new UnsignedIntegerFourBytes(state.getClockStyle()))};
    }

    private void publishWallpaperChange(WallpaperController.State state) {
        if (closed) return;
        Log.i(TAG, "publish RenderingControl wallpaper event: WallpaperId=" + state.getId()
                + " source=" + state.getSource() + " ids=" + state.getIdsJson()
                + " ClockId=" + state.getClockId() + " ClockStyle=" + state.getClockStyle());
        getLastChange().setEventedValue(getDefaultInstanceID(), wallpaperValues(state));
        getLastChange().fire(getPropertyChangeSupport());
    }

    private void publishRoleChange(int roleId) {
        if (closed) {
            return;
        }
        Log.i(TAG, "publish RenderingControl role event: RoleId=" + roleId);
        getLastChange().setEventedValue(getDefaultInstanceID(),
                new RenderingControlVariable.RoleId(new UnsignedIntegerTwoBytes(roleId)));
        getLastChange().fire(getPropertyChangeSupport());
    }

    @Override
    public void appendCurrentState(LastChange lastChange, UnsignedIntegerFourBytes instanceId) throws Exception {
        super.appendCurrentState(lastChange, instanceId);
        lastChange.setEventedValue(instanceId, new RenderingControlVariable.RoleId(getRoleId(instanceId)));
        lastChange.setEventedValue(instanceId, wallpaperValues(wallpaperController.getConfirmedState()));
        lastChange.setEventedValue(instanceId, deviceValues());
    }

    private EventedValue[] deviceValues() {
        String name = SWDeviceStatus.deviceName == null ? "" : SWDeviceStatus.deviceName;
        // Standalone firmware uses the device name as its group name.
        List<EventedValue> values = new ArrayList<>();
        values.add(new RenderingControlVariable.DeviceName(name));
        values.add(new RenderingControlVariable.GroupName(name));
        String route = reportedRoute;
        if (route != null) values.add(new RenderingControlVariable.Route(route));
        return values.toArray(new EventedValue[0]);
    }

    /** Sample confirmed app state on the main thread, then publish under the service lock. */
    public void refreshState() throws RenderingControlException {
        if (closed) return;
        List<EventedValue> snapshot = new ArrayList<>(Arrays.asList(deviceValues()));
        UnsignedIntegerTwoBytes volume = getVolume(getDefaultInstanceID(), Channel.Master.name());
        snapshot.add(new RenderingControlVariable.Volume(new ChannelVolume(Channel.Master, volume.getValue().intValue())));
        snapshot.add(new RenderingControlVariable.Mute(new ChannelMute(Channel.Master, volume.getValue() == 0L)));
        EventedValue[] values = snapshot.toArray(new EventedValue[0]);
        eventDispatcher.accept(() -> {
            if (!closed) statePublisher.publish(getLastChange(), getPropertyChangeSupport(), values);
        });
    }

    /** Route is a vendor protocol field, not an inferred WIFI/BT audio-mode mapping. */
    public void reportRouteChange(String route) {
        if (route == null) throw new IllegalArgumentException("Route is required");
        reportedRoute = route;
        if (!closed) eventDispatcher.accept(() -> {
            if (!closed) statePublisher.publish(getLastChange(), getPropertyChangeSupport(),
                    new RenderingControlVariable.Route(route));
        });
    }

    /** Grouping integrations must report actual membership changes, one UUID per event. */
    public void reportSlaveChange(String action, String uuid, Integer volume, Long timestamp) {
        if (action == null || action.isEmpty() || uuid == null || uuid.isEmpty()
                || (volume != null && (volume < 0 || volume > 100))
                || (timestamp != null && timestamp < 0)) {
            throw new IllegalArgumentException("Invalid slave event");
        }
        RenderingControlVariable.Slave value = new RenderingControlVariable.Slave(action, uuid, volume, timestamp);
        if (!closed) eventDispatcher.accept(() -> {
            if (closed) return;
            getLastChange().setEventedValue(0, value);
            getLastChange().fire(getPropertyChangeSupport());
        });
    }

    @Override
    public UnsignedIntegerTwoBytes getRoleId(UnsignedIntegerFourBytes instanceId) throws RenderingControlException {
        validateRoleInstanceId(instanceId);
        return new UnsignedIntegerTwoBytes(roleController.getRoleId());
    }

    @Override
    public void setRoleId(UnsignedIntegerFourBytes instanceId, UnsignedIntegerTwoBytes roleId)
            throws RenderingControlException {
        validateRoleInstanceId(instanceId);
        if (roleId == null) {
            throw new RenderingControlException(ErrorCode.INVALID_ARGS, "DesiredRoleId is required");
        }
        roleController.requestRoleId(roleId.getValue().intValue());
    }

    @Override
    public UnsignedIntegerFourBytes getWallpaperId(UnsignedIntegerFourBytes instanceId)
            throws RenderingControlException {
        validateRoleInstanceId(instanceId);
        return new UnsignedIntegerFourBytes(wallpaperController.getConfirmedState().getId());
    }

    @Override
    public UnsignedIntegerFourBytes getClockId(UnsignedIntegerFourBytes instanceId)
            throws RenderingControlException {
        validateRoleInstanceId(instanceId);
        return new UnsignedIntegerFourBytes(wallpaperController.getConfirmedState().getClockId());
    }

    @Override
    public UnsignedIntegerFourBytes getClockStyle(UnsignedIntegerFourBytes instanceId)
            throws RenderingControlException {
        validateRoleInstanceId(instanceId);
        return new UnsignedIntegerFourBytes(wallpaperController.getConfirmedState().getClockStyle());
    }

    private void validateRoleInstanceId(UnsignedIntegerFourBytes instanceId) throws RenderingControlException {
        if (instanceId == null || instanceId.getValue() != 0L) {
            throw new RenderingControlException(RenderingControlErrorCode.INVALID_INSTANCE_ID);
        }
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

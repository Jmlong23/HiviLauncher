package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.entity.AbstractPlayQueue;
import com.ljm.audiotoollib.upnpserver.entity.SWActionException;
import com.ljm.audiotoollib.upnpserver.entity.PlayQueueVariable;
import com.ljm.audiotoollib.upnpserver.entity.PlayMusicListType;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.EventedValue;
import java.util.function.Consumer;

public class PlayQueueServiceImpl extends AbstractPlayQueue {
    private final static String TAG = "SWPlayQueueServiceImpl";

    private final PlayQueueController playQueueController;
    private final Consumer<Runnable> eventDispatcher;
    private final StateEventPublisher statePublisher = new StateEventPublisher();
    private volatile boolean closed;

    public PlayQueueServiceImpl(LastChange lastChange, PlayQueueController renderControlManager) {
        this(lastChange, renderControlManager, Runnable::run);
    }

    public PlayQueueServiceImpl(LastChange lastChange, PlayQueueController renderControlManager,
                                Consumer<Runnable> eventDispatcher) {
        super(lastChange);
        this.playQueueController = renderControlManager;
        this.eventDispatcher = eventDispatcher;
    }

    private EventedValue[] currentValues() {
        PlayMusicListType queue = playQueueController.getPlayMusicList();
        String name = queue.getCurPlayListName();
        int index = playQueueController.getCurrentTrackSnapshot().getIndex();
        return new EventedValue[]{
                new PlayQueueVariable.CurrentPlayListName(name == null ? "" : name),
                new PlayQueueVariable.LoopMode(new UnsignedIntegerFourBytes(playQueueController.getQueueLoopMode())),
                new PlayQueueVariable.CurrentIndex(new UnsignedIntegerFourBytes(Math.max(0, index)))};
    }

    @Override
    public void appendCurrentState(LastChange lastChange, UnsignedIntegerFourBytes instanceId) {
        lastChange.setEventedValue(instanceId, currentValues());
    }

    public void refreshState() {
        if (closed) return;
        EventedValue[] values = currentValues();
        eventDispatcher.accept(() -> {
            if (!closed) statePublisher.publish(getLastChange(), getPropertyChangeSupport(), values);
        });
    }

    public void close() {
        closed = true;
    }

    @Override
    public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
        return playQueueController.getPqTransportCurrentInstanceIds();
    }

    @Override
    public void CreateQueue(String queueContext) throws SWActionException {
        Log.i(TAG, "CreateQueue queueContextLen=" + (queueContext == null ? 0 : queueContext.length())
                + ", hash=" + (queueContext == null ? "0" : Integer.toHexString(queueContext.hashCode())));
        playQueueController.createQueue(queueContext);
        refreshState();
    }

    @Override
    public void AppendTracksInQueue(String queueContext) throws SWActionException {
        Log.i(TAG,"AppendTracksInQueue");
        playQueueController.AppendTracksInQueue(queueContext);
        refreshState();
    }

    @Override
    public void SetQueueLoopMode(UnsignedIntegerFourBytes loopMode) throws SWActionException {
        if (loopMode == null || loopMode.getValue() > 2L) {
            throw new SWActionException(ErrorCode.INVALID_ARGS,
                    "LoopMode must be 0, 1 or 2");
        }
        Log.i(TAG,"SetQueueLoopMode loopMode: " + loopMode.getValue().intValue());
        playQueueController.setQueueLoopMode(loopMode.getValue().intValue());
        refreshState();
    }

    @Override
    public UnsignedIntegerFourBytes GetQueueLoopMode() throws SWActionException {
        Log.i(TAG,"GetQueueLoopMode");
        return new UnsignedIntegerFourBytes(playQueueController.getQueueLoopMode());
    }

    @Override
    public String BrowseQueue(String queueName) throws SWActionException {
        Log.i(TAG,"BrowseQueue queueName: " + queueName );
        return playQueueController.browseQueue(queueName);
    }

    @Override
    public void RemoveTracksInQueue(String queueName, UnsignedIntegerFourBytes start, UnsignedIntegerFourBytes end) throws SWActionException {
        Log.i(TAG,"RemoveTracksInQueue queueName: " + queueName + " start: " + start.getValue().intValue() + " end: " + end.getValue().intValue());
        playQueueController.removeTracksInQueue(queueName,start.getValue().intValue(), end.getValue().intValue());
        refreshState();
    }

    @Override
    public void PlayQueueWithIndex(String QueueName, UnsignedIntegerFourBytes index) throws SWActionException {
        Log.i(TAG,"");
        playQueueController.PlayQueueWithIndex(QueueName, index.getValue().intValue());
        refreshState();
    }


}

package com.ljm.audiotoollib.upnpserver.service;

import android.util.Log;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.entity.AbstractPlayQueue;
import com.ljm.audiotoollib.upnpserver.entity.SWActionException;

public class PlayQueueServiceImpl extends AbstractPlayQueue {
    private final static String TAG = "SWPlayQueueServiceImpl";

    private final PlayQueueController playQueueController;

    public PlayQueueServiceImpl(LastChange lastChange, PlayQueueController renderControlManager) {
        super(lastChange);
        this.playQueueController = renderControlManager;
    }

    @Override
    public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
        return playQueueController.getPqTransportCurrentInstanceIds();
    }

    @Override
    public void CreateQueue(String queueContext) throws SWActionException {
        Log.i(TAG,"queueContext: " + queueContext);
        playQueueController.createQueue(queueContext);
    }

    @Override
    public void AppendTracksInQueue(String queueContext) throws SWActionException {
        Log.i(TAG,"AppendTracksInQueue");
        playQueueController.AppendTracksInQueue(queueContext);
    }

    @Override
    public void SetQueueLoopMode(UnsignedIntegerFourBytes loopMode) throws SWActionException {
        Log.i(TAG,"SetQueueLoopMode loopMode: " + loopMode.getValue().intValue());
        playQueueController.setQueueLoopMode(loopMode.getValue().intValue());
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
    }

    @Override
    public void PlayQueueWithIndex(String QueueName, UnsignedIntegerFourBytes index) throws SWActionException {
        Log.i(TAG,"");
        playQueueController.PlayQueueWithIndex(QueueName, index.getValue().intValue());
    }


}

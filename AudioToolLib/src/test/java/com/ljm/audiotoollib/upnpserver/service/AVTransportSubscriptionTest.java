package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.AnnotationLocalServiceBinder;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.CancelReason;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.LocalGENASubscription;
import com.ljm.audiotoollib.upnpserver.cling.model.message.gena.OutgoingEventRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.UpnpHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalService;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportVariable;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeAwareServiceManager;
import com.ljm.audiotoollib.upnpserver.cling.support.model.MediaInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PositionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportAction;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportState;
import com.ljm.audiotoollib.upnpserver.cling.support.model.TransportStatus;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.GENAEventProcessorImpl;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;
import com.ljm.audiotoollib.upnpserver.listener.OnMediaControlListener;
import com.ljm.audiotoollib.upnpserver.listener.OnVideoControlListener;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AVTransportSubscriptionTest {
    private AVTransportController controller;
    private AVTransportServiceImpl implementation;
    private LocalService<AVTransportServiceImpl> service;
    private LastChangeAwareServiceManager<AVTransportServiceImpl> serviceManager;
    private final Queue<Runnable> pendingEvents = new ArrayDeque<>();
    private MediaControl mediaControl;

    @Before
    public void setUp() {
        controller = new AVTransportController();
        mediaControl = new MediaControl();
        controller.setOnMediaControllerListener(mediaControl);
        RenderControlManager renderControlManager = new RenderControlManager();
        renderControlManager.addControl(controller);
        implementation = new AVTransportServiceImpl(new LastChange(new AVTransportLastChangeParser()),
                renderControlManager, pendingEvents::add) {
            @Override
            public MediaInfo getMediaInfo(UnsignedIntegerFourBytes instanceId) {
                return new MediaInfo();
            }

            @Override
            public PositionInfo getPositionInfo(UnsignedIntegerFourBytes instanceId) {
                return new PositionInfo();
            }
        };
        service = new AnnotationLocalServiceBinder().read(AVTransportServiceImpl.class);
        serviceManager = new LastChangeAwareServiceManager<AVTransportServiceImpl>(service,
                new AVTransportLastChangeParser()) {
            @Override
            protected AVTransportServiceImpl createServiceInstance() {
                return implementation;
            }
        };
        service.setManager(serviceManager);
        serviceManager.getImplementation();
        report(TransportState.NO_MEDIA_PRESENT, TransportStatus.OK);
    }

    @Test
    public void initialSubscriptionContainsCurrentStateAndNotifyEnvelope() throws Exception {
        report(TransportState.PLAYING, TransportStatus.OK);
        RecordingSubscription subscription = subscribe();
        assertEquals("PLAYING", state(subscription.initialEvent));
        OutgoingEventRequestMessage message = new OutgoingEventRequestMessage(subscription,
                subscription.getCallbackURLs().get(0));
        new GENAEventProcessorImpl().writeBody(message);
        assertEquals("NOTIFY", message.getOperation().getMethod().name());
        assertEquals("0", message.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
        assertEquals(subscription.getSubscriptionId(),
                message.getHeaders().getFirstHeaderString(UpnpHeader.Type.SID));
        assertEquals("upnp:event", message.getHeaders().getFirstHeaderString(UpnpHeader.Type.NT));
        assertEquals("upnp:propchange", message.getHeaders().getFirstHeaderString(UpnpHeader.Type.NTS));
        assertEquals(1800, subscription.getActualDurationSeconds());
        assertTrue(message.getBodyString().contains("&lt;Event"));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(message.getBodyString())));
        String lastChange = document.getElementsByTagName("LastChange").item(0).getTextContent();
        assertEquals("PLAYING", state(lastChange));
        Document event = factory.newDocumentBuilder().parse(new InputSource(new StringReader(lastChange)));
        assertEquals(AVTransportLastChangeParser.NAMESPACE_URI, event.getDocumentElement().getNamespaceURI());
    }

    @Test
    public void emptyInitialSubscriptionHasNoPlayableActions() throws Exception {
        RecordingSubscription subscription = subscribe();
        assertEquals("NO_MEDIA_PRESENT", state(subscription.initialEvent));
        assertArrayEquals(new TransportAction[0], parse(subscription.initialEvent)
                .getEventedValue(0, AVTransportVariable.CurrentTransportActions.class).getValue());
    }

    @Test
    public void rapidChangesReachEverySubscriberWithoutModerationLoss() throws Exception {
        RecordingSubscription first = subscribe();
        RecordingSubscription second = subscribe();
        for (TransportState state : new TransportState[]{TransportState.TRANSITIONING,
                TransportState.PLAYING, TransportState.PAUSED_PLAYBACK, TransportState.PLAYING,
                TransportState.STOPPED, TransportState.NO_MEDIA_PRESENT}) {
            report(state, TransportStatus.OK);
        }
        List<String> expected = Arrays.asList("TRANSITIONING", "PLAYING", "PAUSED_PLAYBACK",
                "PLAYING", "STOPPED", "NO_MEDIA_PRESENT");
        assertEquals(expected, first.states);
        assertEquals(expected, second.states);
        assertEquals(0, service.getStateVariable("LastChange").getEventDetails().getEventMaximumRateMilliseconds());
    }

    @Test
    public void repeatedStateDoesNotEmitDuplicateEvents() throws Exception {
        RecordingSubscription subscription = subscribe();
        report(TransportState.PLAYING, TransportStatus.OK);
        report(TransportState.PLAYING, TransportStatus.OK);
        controller.getTransportInfo();
        controller.getInfoEx();
        drainEvents();
        assertEquals(Collections.singletonList("PLAYING"), subscription.states);
    }

    @Test
    public void errorAndRecoveryNotifyEvenWhenStateRemainsStopped() throws Exception {
        RecordingSubscription subscription = subscribe();
        report(TransportState.STOPPED, TransportStatus.OK);
        report(TransportState.STOPPED, TransportStatus.ERROR_OCCURRED);
        assertEquals(TransportStatus.ERROR_OCCURRED, controller.getTransportInfo().getCurrentTransportStatus());
        report(TransportState.STOPPED, TransportStatus.OK);
        assertEquals(Arrays.asList("OK", "ERROR_OCCURRED", "OK"), subscription.statuses);
    }

    @Test
    public void pausedEventIncludesResumeAndStopActions() throws Exception {
        RecordingSubscription subscription = subscribe();
        report(TransportState.PAUSED_PLAYBACK, TransportStatus.OK);
        assertArrayEquals(new TransportAction[]{TransportAction.Play, TransportAction.Seek, TransportAction.Stop},
                parse(subscription.events.get(0))
                        .getEventedValue(0, AVTransportVariable.CurrentTransportActions.class).getValue());
    }

    @Test
    public void unsubscribeDoesNotAffectOtherSubscribers() throws Exception {
        RecordingSubscription first = subscribe();
        RecordingSubscription second = subscribe();
        first.end(null);
        report(TransportState.PLAYING, TransportStatus.OK);
        assertTrue(first.states.isEmpty());
        assertEquals(Collections.singletonList("PLAYING"), second.states);
        second.setSubscriptionDuration(600);
        assertEquals(600, second.getActualDurationSeconds());
    }

    @Test
    public void audioCallbacksCannotOverwriteVideoSession() throws Exception {
        RecordingSubscription subscription = subscribe();
        controller.setAVTransportURI("http://speaker/movie.mp4", "");
        controller.reportVideoTransportState(TransportState.PLAYING);
        controller.reportAudioTransportState(TransportState.PAUSED_PLAYBACK, TransportStatus.OK);
        controller.reportAudioTransportState(TransportState.STOPPED, TransportStatus.ERROR_OCCURRED);
        drainEvents();
        assertEquals(Arrays.asList("TRANSITIONING", "PLAYING"), subscription.states);
        assertEquals(TransportState.PLAYING, controller.getTransportInfo().getCurrentTransportState());
    }

    @Test
    public void videoQueryDiscoveredStateAlsoNotifiesSubscribers() throws Exception {
        RecordingSubscription subscription = subscribe();
        controller.setAVTransportURI("http://speaker/movie.mp4", "");
        VideoControl videoControl = new VideoControl();
        videoControl.state = TransportState.PAUSED_PLAYBACK;
        controller.setOnVideoControlListener(videoControl);
        assertEquals(TransportState.PAUSED_PLAYBACK, controller.getTransportInfo().getCurrentTransportState());
        drainEvents();
        assertEquals(Arrays.asList("TRANSITIONING", "PAUSED_PLAYBACK"), subscription.states);
    }

    @Test
    public void audioStopInvokesPlayerAndWaitsForActualState() throws Exception {
        RecordingSubscription subscription = subscribe();
        report(TransportState.PLAYING, TransportStatus.OK);
        controller.stop();
        assertTrue(mediaControl.stopCalled);
        assertEquals(TransportState.PLAYING, controller.getTransportInfo().getCurrentTransportState());
        report(TransportState.STOPPED, TransportStatus.OK);
        assertEquals(Arrays.asList("PLAYING", "STOPPED"), subscription.states);
    }

    @Test
    public void audioCommandsDoNotPublishUnconfirmedPlaybackStates() throws Exception {
        RecordingSubscription subscription = subscribe();
        report(TransportState.PLAYING, TransportStatus.OK);
        controller.play("1");
        controller.pause();
        drainEvents();
        assertEquals(Collections.singletonList("PLAYING"), subscription.states);
    }

    @Test
    public void closingServiceDetachesListenerAndDiscardsPendingEvents() throws Exception {
        RecordingSubscription subscription = subscribe();
        controller.reportAudioTransportState(TransportState.PLAYING, TransportStatus.OK);
        assertFalse(pendingEvents.isEmpty());
        implementation.close();
        drainEvents();
        controller.reportAudioTransportState(TransportState.PAUSED_PLAYBACK, TransportStatus.OK);
        assertTrue(subscription.states.isEmpty());
        assertTrue(pendingEvents.isEmpty());
    }

    @Test
    public void closingOldServiceDoesNotDetachReplacementListener() {
        List<TransportState> replacementStates = new ArrayList<>();
        controller.setOnTransportStateChangedListener((state, status, actions) -> replacementStates.add(state));
        implementation.close();
        controller.reportAudioTransportState(TransportState.PLAYING, TransportStatus.OK);
        assertEquals(Collections.singletonList(TransportState.PLAYING), replacementStates);
    }

    private RecordingSubscription subscribe() throws Exception {
        RecordingSubscription subscription = new RecordingSubscription(service);
        subscription.registerOnService();
        return subscription;
    }

    private void report(TransportState state, TransportStatus status) {
        mediaControl.info.setCurrentTransportState(state);
        mediaControl.info.setCurrentTransportStatus(status);
        controller.reportAudioTransportState(state, status);
        drainEvents();
    }

    private void drainEvents() {
        while (!pendingEvents.isEmpty()) {
            Runnable event = pendingEvents.remove();
            try {
                serviceManager.execute(manager -> event.run());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static LastChange parse(String event) throws Exception {
        return new LastChange(new AVTransportLastChangeParser(), event);
    }

    private static String state(String event) throws Exception {
        return parse(event).getEventedValue(0, AVTransportVariable.TransportState.class).getValue().name();
    }

    private static class RecordingSubscription extends LocalGENASubscription {
        final List<String> events = new ArrayList<>();
        final List<String> states = new ArrayList<>();
        final List<String> statuses = new ArrayList<>();
        final String initialEvent;

        RecordingSubscription(LocalService service) throws Exception {
            super(service, 1800, Collections.singletonList(new URL("http://phone:8080/dlna/callback/avtransport")));
            initialEvent = getCurrentValues().get("LastChange").toString();
        }

        @Override
        public void established() {
        }

        @Override
        public void ended(CancelReason reason) {
        }

        @Override
        public void eventReceived() {
            String event = getCurrentValues().get("LastChange").toString();
            events.add(event);
            try {
                states.add(state(event));
                statuses.add(parse(event).getEventedValue(0, AVTransportVariable.TransportStatus.class)
                        .getValue().name());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static class MediaControl implements OnMediaControlListener {
        final InfoEx info = new InfoEx();
        boolean stopCalled;

        @Override public void play() { }
        @Override public void pause() { }
        @Override public void stop() { stopCalled = true; }
        @Override public void previous() { }
        @Override public void next() { }
        @Override public void seek(long position) { }
        @Override public void onLyricReceived(String lyric, String terraceType) { }
        @Override public InfoEx getInfoEx() { return info; }
    }

    private static class VideoControl implements OnVideoControlListener {
        TransportState state;

        @Override public void playVideo() { }
        @Override public void pauseVideo() { }
        @Override public void previousVideo() { }
        @Override public void nextVideo() { }
        @Override public void seekVideo(long position) { }
        @Override public long getPositionVideo() { return 0; }
        @Override public long getDurationVideo() { return 0; }
        @Override public TransportState getVideoTransportState() { return state; }
        @Override public void setAVTransportURI(String currentURI, String metadata) { }
        @Override public void stop() { }
    }
}

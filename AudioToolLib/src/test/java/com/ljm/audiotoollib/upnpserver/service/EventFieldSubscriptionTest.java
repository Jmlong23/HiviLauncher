package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.AnnotationLocalServiceBinder;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.CancelReason;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.LocalGENASubscription;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalService;
import com.ljm.audiotoollib.upnpserver.cling.model.message.gena.OutgoingEventRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.UpnpHeader;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.GENAEventProcessorImpl;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.avtransport.lastchange.AVTransportVariable;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeAwareServiceManager;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeDelegator;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.model.MediaInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PositionInfo;
import com.ljm.audiotoollib.upnpserver.cling.support.model.StorageMedium;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlVariable;
import com.ljm.audiotoollib.upnpserver.entity.InfoEx;
import com.ljm.audiotoollib.upnpserver.entity.PlayQueueLastChangeParser;
import com.ljm.audiotoollib.upnpserver.entity.PlayQueueVariable;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;
import com.ljm.audiotoollib.upnpserver.entity.TrackINFO_Type;
import com.ljm.audiotoollib.upnpserver.listener.OnPlayMusicByIndexListener;
import com.ljm.audiotoollib.upnpserver.entity.PlayMusicListType;
import org.junit.Test;
import org.w3c.dom.Element;
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
import static org.junit.Assert.*;

public class EventFieldSubscriptionTest {
    private final Queue<Runnable> pending = new ArrayDeque<>();

    @Test public void queueInitialAndIncrementalEventsUseQueueNamespaceAndRoot() throws Exception {
        PlayQueueController controller = new PlayQueueController(null);
        TrackINFO_Type track = new TrackINFO_Type();
        track.setURL("http://host/song");
        controller.getPlayMusicList().replacePQMusicList(Arrays.asList(track, track, track));
        controller.getPlayMusicList().setCurPlayListName("Rock & \"Live\" <2026>");
        controller.setOnPlayMusicByIndexListener(new OnPlayMusicByIndexListener() {
            int mode;
            @Override public void onPlay(PlayMusicListType queue) { }
            @Override public int getLoopMode() { return mode; }
            @Override public void setLoopMode(int value) { mode = value; }
        });
        PlayQueueServiceImpl implementation = new PlayQueueServiceImpl(
                new LastChange(new PlayQueueLastChangeParser()), controller, pending::add);
        LocalService<PlayQueueServiceImpl> service = bind(PlayQueueServiceImpl.class, implementation, new PlayQueueLastChangeParser());
        RecordingSubscription first = subscribe(service);
        RecordingSubscription second = subscribe(service);
        assertFalse(first.getSubscriptionId().equals(second.getSubscriptionId()));
        Element root = xml(first.initial).getDocumentElement();
        assertEquals(PlayQueueLastChangeParser.NAMESPACE_URI, root.getNamespaceURI());
        assertEquals(1, root.getElementsByTagName("QueueID").getLength());
        assertEquals(0, root.getElementsByTagName("InstanceID").getLength());
        assertEquals("Rock & \"Live\" <2026>", queue(first.initial).getEventedValue(0,
                PlayQueueVariable.CurrentPlayListName.class).getValue());
        implementation.refreshState(); drain(service);
        first.events.clear(); second.events.clear();
        implementation.PlayQueueWithIndex("CurrentQueue", new UnsignedIntegerFourBytes(1));
        implementation.PlayQueueWithIndex("CurrentQueue", new UnsignedIntegerFourBytes(2));
        drain(service);
        assertEquals(2, first.events.size());
        assertEquals(first.events, second.events);
        assertEquals(Arrays.asList("0", "1", "2", "3"), first.sequences);
        assertEquals(1L, queue(first.events.get(0)).getEventedValue(0, PlayQueueVariable.CurrentIndex.class).getValue().getValue().longValue());
        assertEquals(2L, queue(first.events.get(1)).getEventedValue(0, PlayQueueVariable.CurrentIndex.class).getValue().getValue().longValue());
        implementation.SetQueueLoopMode(new UnsignedIntegerFourBytes(2)); drain(service);
        assertEquals(2L, queue(first.events.get(2)).getEventedValue(0, PlayQueueVariable.LoopMode.class).getValue().getValue().longValue());
        implementation.refreshState(); drain(service);
        assertEquals(3, first.events.size());
        controller.getPlayMusicList().setCurPlayIndex(0);
        implementation.refreshState(); drain(service);
        assertEquals(4, first.events.size());
        assertEquals(0, service.getStateVariable("LastChange").getEventDetails().getEventMaximumRateMilliseconds());
        controller.getPlayMusicList().setCurPlayIndex(1);
        implementation.refreshState(); implementation.close(); drain(service);
        assertEquals(4, first.events.size());
    }

    @Test public void renderingEventsPublishConfirmedVolumeNamesRouteAndSlaveAttributes() throws Exception {
        String originalName = SWDeviceStatus.deviceName;
        int[] volume = {35};
        AudioRenderServiceImpl implementation = new AudioRenderServiceImpl(
                new LastChange(new RenderingControlLastChangeParser()), new RenderControlManager(),
                new RoleController(), pending::add) {
            @Override public UnsignedIntegerFourBytes[] getCurrentInstanceIds() { return new UnsignedIntegerFourBytes[]{getDefaultInstanceID()}; }
            @Override public UnsignedIntegerTwoBytes getVolume(UnsignedIntegerFourBytes id, String channel) { return new UnsignedIntegerTwoBytes(volume[0]); }
            @Override public boolean getMute(UnsignedIntegerFourBytes id, String channel) { return volume[0] == 0; }
        };
        try {
            SWDeviceStatus.deviceName = "Living & \"Room\"";
            LocalService<AudioRenderServiceImpl> service = bind(AudioRenderServiceImpl.class, implementation, new RenderingControlLastChangeParser());
            RecordingSubscription subscription = subscribe(service);
            LastChange initial = rcs(subscription.initial);
            assertEquals(SWDeviceStatus.deviceName, initial.getEventedValue(0, RenderingControlVariable.DeviceName.class).getValue());
            assertEquals(SWDeviceStatus.deviceName, initial.getEventedValue(0, RenderingControlVariable.GroupName.class).getValue());
            assertNull(initial.getEventedValue(0, RenderingControlVariable.Slave.class));
            assertNull(initial.getEventedValue(0, RenderingControlVariable.Route.class));
            implementation.reportRouteChange("0"); drain(service);
            assertEquals("0", rcs(subscription.events.get(0)).getEventedValue(0, RenderingControlVariable.Route.class).getValue());
            RecordingSubscription later = subscribe(service);
            assertEquals("0", rcs(later.initial).getEventedValue(0, RenderingControlVariable.Route.class).getValue());
            implementation.refreshState(); drain(service); subscription.events.clear();
            volume[0] = 0; SWDeviceStatus.deviceName = "Bedroom";
            implementation.refreshState(); drain(service);
            LastChange change = rcs(subscription.events.get(0));
            assertEquals(0, change.getEventedValue(0, RenderingControlVariable.Volume.class).getValue().getVolume().intValue());
            assertTrue(change.getEventedValue(0, RenderingControlVariable.Mute.class).getValue().getMute());
            assertEquals("Bedroom", change.getEventedValue(0, RenderingControlVariable.DeviceName.class).getValue());
            implementation.refreshState(); drain(service); assertEquals(1, subscription.events.size());
            implementation.reportSlaveChange("add", "uuid-one", 20, 123L);
            implementation.reportSlaveChange("add", "uuid-two", 40, 124L); drain(service);
            assertEquals(3, subscription.events.size());
            Element slave = (Element) xml(subscription.events.get(1)).getElementsByTagName("Slave").item(0);
            assertEquals("uuid-one", slave.getAttribute("val"));
            assertEquals("add", slave.getAttribute("action"));
            assertEquals("20", slave.getAttribute("SlaveVolume"));
            assertEquals("123", slave.getAttribute("TimeStamp"));
            implementation.refreshState(); implementation.close(); drain(service);
            assertEquals(3, subscription.events.size());
        } finally { implementation.close(); SWDeviceStatus.deviceName = originalName; }
    }

    @Test public void mediaEventsContainAllPhoneFieldsAndIncrementOnlyProgress() throws Exception {
        String metadata = "<DIDL-Lite><item title=\"A &amp; B\"/></DIDL-Lite>";
        InfoEx info = new InfoEx(); info.setTrackSource("Spotify");
        String[] time = {"00:00:04"};
        RenderControlManager controls = new RenderControlManager();
        controls.addControl(new AVTransportController());
        AVTransportServiceImpl implementation = new AVTransportServiceImpl(new LastChange(new AVTransportLastChangeParser()), controls, pending::add) {
            @Override public PositionInfo getPositionInfo(UnsignedIntegerFourBytes id) {
                return new PositionInfo(1, "00:03:00", metadata, "http://host/song?a=1&b=2", time[0], time[0], 0, 0);
            }
            @Override public MediaInfo getMediaInfo(UnsignedIntegerFourBytes id) {
                return new MediaInfo("http://host/song?a=1&b=2", metadata, new UnsignedIntegerFourBytes(1), "00:03:00", StorageMedium.NETWORK);
            }
            @Override public InfoEx getInfoEx(UnsignedIntegerFourBytes id) { return info; }
        };
        LocalService<AVTransportServiceImpl> service = bind(AVTransportServiceImpl.class, implementation, new AVTransportLastChangeParser());
        RecordingSubscription subscription = subscribe(service);
        LastChange initial = avt(subscription.initial);
        assertEquals(metadata, initial.getEventedValue(0, AVTransportVariable.CurrentTrackMetaData.class).getValue());
        assertEquals(metadata, initial.getEventedValue(0, AVTransportVariable.AVTransportURIMetaData.class).getValue());
        assertEquals("00:03:00", initial.getEventedValue(0, AVTransportVariable.CurrentMediaDuration.class).getValue());
        assertEquals("00:00:04", initial.getEventedValue(0, AVTransportVariable.RelativeTimePosition.class).getValue());
        assertEquals(StorageMedium.NETWORK, initial.getEventedValue(0, AVTransportVariable.PlaybackStorageMedium.class).getValue());
        assertEquals("Spotify", initial.getEventedValue(0, AVTransportVariable.TrackSource.class).getValue());
        assertNull(initial.getEventedValue(0, AVTransportVariable.A_ARG_TYPE_SeekTarget.class));
        implementation.refreshState(); drain(service); subscription.events.clear();
        time[0] = "00:00:05"; implementation.refreshState(); drain(service);
        assertEquals(1, subscription.events.size());
        LastChange change = avt(subscription.events.get(0));
        assertEquals("00:00:05", change.getEventedValue(0, AVTransportVariable.RelativeTimePosition.class).getValue());
        assertNull(change.getEventedValue(0, AVTransportVariable.CurrentTrackURI.class));
        implementation.refreshState(); drain(service); assertEquals(1, subscription.events.size());
        time[0] = "00:00:06"; implementation.refreshState(); implementation.close(); drain(service);
        assertEquals(1, subscription.events.size());
    }

    @Test public void queueSchemaRejectsWrongRoot() throws Exception {
        try {
            queue("<Event xmlns=\"" + PlayQueueLastChangeParser.NAMESPACE_URI + "\"><InstanceID val=\"0\"/></Event>");
            fail("Invalid QueueID root should be rejected");
        } catch (org.seamless.xml.ParserException expected) { }
        try {
            avt("<Event xmlns=\"" + AVTransportLastChangeParser.NAMESPACE_URI
                    + "\"><InstanceID val=\"0\"><TransportState val=\"LOADING\"/></InstanceID></Event>");
            fail("Standard AVT state validation must remain enabled");
        } catch (org.seamless.xml.ParserException expected) { }
    }

    @Test public void seekPublishesOnlyAcceptedTargetAndRejectsUnsupportedMode() throws Exception {
        AVTransportController controller = new AVTransportController();
        long[] seekPosition = {-1};
        controller.setOnMediaControllerListener(new com.ljm.audiotoollib.upnpserver.listener.OnMediaControlListener() {
            @Override public void play() { }
            @Override public void pause() { }
            @Override public void stop() { }
            @Override public void previous() { }
            @Override public void next() { }
            @Override public void seek(long position) { seekPosition[0] = position; }
            @Override public void onLyricReceived(String lyric, String terrace) { }
            @Override public InfoEx getInfoEx() { return new InfoEx(); }
        });
        RenderControlManager controls = new RenderControlManager(); controls.addControl(controller);
        AVTransportServiceImpl implementation = new AVTransportServiceImpl(new LastChange(new AVTransportLastChangeParser()), controls, pending::add) {
            @Override public PositionInfo getPositionInfo(UnsignedIntegerFourBytes id) { return new PositionInfo(); }
            @Override public MediaInfo getMediaInfo(UnsignedIntegerFourBytes id) { return new MediaInfo(); }
        };
        LocalService<AVTransportServiceImpl> service = bind(AVTransportServiceImpl.class, implementation, new AVTransportLastChangeParser());
        RecordingSubscription subscription = subscribe(service);
        implementation.seek(new UnsignedIntegerFourBytes(0), "REL_TIME", "00:01:23"); drain(service);
        assertEquals(83000L, seekPosition[0]);
        assertEquals("00:01:23", avt(subscription.events.get(0))
                .getEventedValue(0, AVTransportVariable.A_ARG_TYPE_SeekTarget.class).getValue());
        try {
            implementation.seek(new UnsignedIntegerFourBytes(0), "TRACK_NR", "2");
            fail("Unsupported seek mode accepted");
        } catch (com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportException expected) {
            assertEquals(710, expected.getErrorCode());
        }
        try {
            implementation.seek(new UnsignedIntegerFourBytes(0), "REL_TIME", "00:99:00");
            fail("Invalid seek target accepted");
        } catch (com.ljm.audiotoollib.upnpserver.cling.support.avtransport.AVTransportException expected) {
            assertEquals(711, expected.getErrorCode());
        }
        drain(service); assertEquals(1, subscription.events.size()); implementation.close();
    }

    @Test public void clearingQueueClearsCachedMediaAndPosition() {
        PlayMusicListType queue = PlayQueueController.getInstance().getPlayMusicList();
        queue.clearPQMusicList();
        AVTransportController controller = new AVTransportController();
        TrackINFO_Type track = new TrackINFO_Type(); track.setURL("http://host/old"); track.setMetaData("old metadata");
        queue.replacePQMusicList(Collections.singletonList(track)); queue.setCurPlayIndex(0);
        assertEquals("http://host/old", controller.getPositionInfo().getTrackURI());
        queue.clearPQMusicList();
        assertEquals("", controller.getPositionInfo().getTrackURI());
        assertEquals("", controller.getPositionInfo().getTrackMetaData());
        assertEquals("00:00:00", controller.getMediaInfo().getMediaDuration());
        assertEquals(0L, controller.getMediaInfo().getNumberOfTracks().getValue().longValue());
        assertEquals(StorageMedium.NONE, controller.getMediaInfo().getPlayMedium());
    }

    private <T extends LastChangeDelegator> LocalService<T> bind(Class<T> type, T implementation, LastChangeParser parser) {
        LocalService<T> service = new AnnotationLocalServiceBinder().read(type);
        service.setManager(new LastChangeAwareServiceManager<T>(service, parser) {
            @Override protected T createServiceInstance() { return implementation; }
        });
        service.getManager().getImplementation();
        return service;
    }

    private void drain(LocalService<?> service) throws Exception {
        while (!pending.isEmpty()) {
            Runnable event = pending.remove(); service.getManager().execute(manager -> event.run());
        }
    }
    private RecordingSubscription subscribe(LocalService<?> service) throws Exception {
        RecordingSubscription subscription = new RecordingSubscription(service); subscription.registerOnService(); return subscription;
    }
    private static LastChange queue(String value) throws Exception { return new LastChange(new PlayQueueLastChangeParser(), value); }
    private static LastChange rcs(String value) throws Exception { return new LastChange(new RenderingControlLastChangeParser(), value); }
    private static LastChange avt(String value) throws Exception { return new LastChange(new AVTransportLastChangeParser(), value); }
    private static org.w3c.dom.Document xml(String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(value)));
    }
    private static class RecordingSubscription extends LocalGENASubscription {
        final String initial;
        final List<String> events = new ArrayList<>();
        final List<String> sequences = new ArrayList<>();
        RecordingSubscription(LocalService<?> service) throws Exception {
            super(service, 1800, Collections.singletonList(new URL("http://phone:8080/events")));
            initial = readNotify();
        }
        private String readNotify() throws Exception {
            OutgoingEventRequestMessage message = new OutgoingEventRequestMessage(this, getCallbackURLs().get(0));
            new GENAEventProcessorImpl().writeBody(message);
            assertEquals("NOTIFY", message.getOperation().getMethod().name());
            assertEquals(getSubscriptionId(), message.getHeaders().getFirstHeaderString(UpnpHeader.Type.SID));
            sequences.add(message.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
            incrementSequence();
            return xml(message.getBodyString()).getElementsByTagName("LastChange").item(0).getTextContent();
        }
        @Override public void established() { }
        @Override public void ended(CancelReason reason) { }
        @Override public void eventReceived() {
            try { events.add(readNotify()); } catch (Exception exception) { throw new AssertionError(exception); }
        }
    }
}

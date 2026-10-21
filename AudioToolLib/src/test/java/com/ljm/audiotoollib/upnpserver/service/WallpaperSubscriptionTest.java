package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.AnnotationLocalServiceBinder;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionInvocation;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.CancelReason;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.LocalGENASubscription;
import com.ljm.audiotoollib.upnpserver.cling.model.message.gena.OutgoingEventRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.UpnpHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalService;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalDevice;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.DeviceIdentity;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.DeviceDetails;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UDN;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UDADeviceType;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerTwoBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChangeAwareServiceManager;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlVariable;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.GENAEventProcessorImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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

public class WallpaperSubscriptionTest {
    private WallpaperController controller;
    private RoleController roles;
    private AudioRenderServiceImpl implementation;
    private LocalService<AudioRenderServiceImpl> service;
    private final Queue<Runnable> pending = new ArrayDeque<>();

    @Before public void setUp() throws Exception {
        controller = new WallpaperController();
        roles = new RoleController();
        implementation = new AudioRenderServiceImpl(new LastChange(new RenderingControlLastChangeParser()),
                new RenderControlManager(), roles, controller, pending::add) {
            @Override public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
                return new UnsignedIntegerFourBytes[]{getDefaultInstanceID()};
            }
            @Override public UnsignedIntegerTwoBytes getVolume(UnsignedIntegerFourBytes id, String channel) {
                return new UnsignedIntegerTwoBytes(50);
            }
            @Override public boolean getMute(UnsignedIntegerFourBytes id, String channel) { return false; }
        };
        service = new AnnotationLocalServiceBinder().read(AudioRenderServiceImpl.class);
        new LocalDevice(new DeviceIdentity(UDN.uniqueSystemIdentifier("wallpaper-subscription-test")),
                new UDADeviceType("MediaRenderer", 1), new DeviceDetails("Wallpaper test speaker"), service);
        service.setManager(new LastChangeAwareServiceManager<AudioRenderServiceImpl>(service,
                new RenderingControlLastChangeParser()) {
            @Override protected AudioRenderServiceImpl createServiceInstance() { return implementation; }
        });
        service.getManager().getImplementation();
    }

    @After public void close() { implementation.close(); }

    @Test public void descriptorAdvertisesWallpaperQueryAndAggregatedFields() throws Exception {
        assertNotNull(service.getAction("GetWallpaperId"));
        assertNotNull(service.getAction("GetClockId"));
        assertNotNull(service.getAction("GetClockStyle"));
        for (String field : new String[]{"WallpaperId", "WallpaperSource", "WallpaperIds", "ClockId", "ClockStyle"}) {
            assertFalse(service.getStateVariable(field).getEventDetails().isSendEvents());
        }
        String descriptor = new UDA10ServiceDescriptorBinderImpl().generate(service);
        assertTrue(descriptor.contains("<name>WallpaperId</name>"));
        assertTrue(descriptor.contains("<name>ClockId</name>"));
        assertTrue(descriptor.contains("<name>ClockStyle</name>"));
        assertTrue(descriptor.contains("<dataType>ui4</dataType>"));
    }

    @Test public void initialSnapshotIncludesWallpaperAndRoleWithSequenceZero() throws Exception {
        controller.reportAppliedWallpaper(preset(495));
        roles.reportRoleId(5);
        drain();
        RecordingSubscription subscription = subscribe();
        assertWallpaper(subscription.initial, 495, "preset", "[495]");
        assertEquals(5, parse(subscription.initial).getEventedValue(0,
                RenderingControlVariable.RoleId.class).getValue().getValue().intValue());
        assertEquals("0", subscription.sequences.get(0));
        assertTrue(subscription.initial.getBodyString().contains("&lt;WallpaperId"));
    }

    @Test public void startupWithoutAppliedWallpaperHasExplicitUnknownState() throws Exception {
        OutgoingEventRequestMessage initial = subscribe().initial;
        assertWallpaper(initial, 0, "unknown", "[]");
        assertClock(initial, 0, 0);
    }

    @Test public void localSelectionWaitsForBothApplicationAndSuccessfulUpload() throws Exception {
        RecordingSubscription subscription = subscribe();
        long version = controller.stageLocalWallpaper(preset(365));
        assertFalse(controller.confirmWallpaperUpload(version));
        assertFalse(controller.isPendingWallpaperUpload(version));
        assertEquals(version, controller.reportAppliedWallpaper(preset(365)));
        assertTrue(controller.isPendingWallpaperUpload(version));
        drain();
        assertTrue(subscription.events.isEmpty());
        assertEquals(0, implementation.getWallpaperId(new UnsignedIntegerFourBytes(0)).getValue().intValue());
        assertTrue(controller.confirmWallpaperUpload(version));
        drain();
        assertWallpaper(subscription.events.get(0), 365, "preset", "[365]");
        assertFalse(controller.confirmWallpaperUpload(version));
        drain();
        assertEquals(1, subscription.events.size());
    }

    @Test public void failedUploadAndMatchingCloudEchoCannotLeakIntoNewSubscription() throws Exception {
        controller.reportAppliedWallpaper(preset(361)); drain();
        long version = controller.stageLocalWallpaper(preset(495));
        assertEquals(version, controller.reportAppliedWallpaper(preset(495)));
        assertEquals(0, controller.reportAppliedWallpaper(preset(495)));
        drain();
        assertWallpaper(subscribe().initial, 361, "preset", "[361]");
        assertTrue(controller.isPendingWallpaperUpload(version));
    }

    @Test public void staleCallbacksCannotConfirmNewSelectionEvenWhenIdRepeats() throws Exception {
        RecordingSubscription subscription = subscribe();
        long first = controller.stageLocalWallpaper(preset(495));
        controller.reportAppliedWallpaper(preset(495));
        long second = controller.stageLocalWallpaper(preset(496));
        controller.reportAppliedWallpaper(preset(496));
        long latest = controller.stageLocalWallpaper(preset(495));
        controller.reportAppliedWallpaper(preset(495));
        assertFalse(controller.confirmWallpaperUpload(first));
        assertFalse(controller.confirmWallpaperUpload(second));
        drain(); assertTrue(subscription.events.isEmpty());
        assertTrue(controller.confirmWallpaperUpload(latest)); drain();
        assertEquals(1, subscription.events.size());
        assertWallpaper(subscription.events.get(0), 495, "preset", "[495]");
    }

    @Test public void cloudReplacementInvalidatesUploadBeforeItsDownloadCompletes() throws Exception {
        RecordingSubscription subscription = subscribe();
        long version = controller.stageLocalWallpaper(preset(495));
        controller.reportAppliedWallpaper(preset(495));
        WallpaperController.State replacement = new WallpaperController.State("custom", 123456);
        controller.prepareWallpaper(replacement);
        assertFalse(controller.confirmWallpaperUpload(version));
        drain(); assertTrue(subscription.events.isEmpty());
        assertEquals(0, controller.reportAppliedWallpaper(replacement)); drain();
        assertWallpaper(subscription.events.get(0), 123456, "custom", "[123456]");
    }

    @Test public void videoFailureInvalidatesUploadAndRetryWaitsForAnotherAppliedFrame() throws Exception {
        RecordingSubscription subscription = subscribe();
        long version = controller.stageLocalWallpaper(preset(365));
        assertEquals(version, controller.reportAppliedWallpaper(preset(365)));
        controller.suspendPendingWallpaperUpload();
        assertFalse(controller.confirmWallpaperUpload(version));
        drain(); assertTrue(subscription.events.isEmpty());
        long retry = controller.reportAppliedWallpaper(preset(365));
        assertTrue(retry > version);
        assertTrue(controller.confirmWallpaperUpload(retry)); drain();
        assertWallpaper(subscription.events.get(0), 365, "preset", "[365]");
    }

    @Test public void consecutiveConfirmedChangesNotifyBothPhonesWithIndependentSequences() throws Exception {
        RecordingSubscription first = subscribe();
        RecordingSubscription second = subscribe();
        for (int id : new int[]{365, 495, 361}) {
            long version = controller.stageLocalWallpaper(preset(id));
            assertEquals(version, controller.reportAppliedWallpaper(preset(id)));
            assertTrue(controller.confirmWallpaperUpload(version));
            drain();
        }
        assertEquals(Arrays.asList("0", "1", "2", "3"), first.sequences);
        assertEquals(first.sequences, second.sequences);
        assertFalse(first.getSubscriptionId().equals(second.getSubscriptionId()));
        for (int i = 0; i < 3; i++) assertEquals(first.events.get(i).getBodyString(), second.events.get(i).getBodyString());
        first.end(null);
        controller.reportAppliedWallpaper(preset(496)); drain();
        assertEquals(3, first.events.size());
        assertEquals(4, second.events.size());
    }

    @Test public void unchangedStateIsSuppressedButPlaylistOrderAndSourceChangesNotify() throws Exception {
        RecordingSubscription subscription = subscribe();
        for (WallpaperController.State state : Arrays.asList(preset(361), preset(361),
                new WallpaperController.State("carousel", 361, 495),
                new WallpaperController.State("carousel", 361, 495),
                new WallpaperController.State("carousel", 361, 496),
                new WallpaperController.State("carousel", 496, 361),
                new WallpaperController.State("custom", 496))) {
            controller.reportAppliedWallpaper(state); drain();
        }
        assertEquals(5, subscription.events.size());
        assertWallpaper(subscription.events.get(2), 361, "carousel", "[361, 496]");
        assertWallpaper(subscription.events.get(3), 496, "carousel", "[496, 361]");
    }

    @Test public void repeatedSuccessfulSelectionDoesNotNotifyTwice() throws Exception {
        RecordingSubscription subscription = subscribe();
        for (int i = 0; i < 2; i++) {
            long version = controller.stageLocalWallpaper(preset(495));
            controller.reportAppliedWallpaper(preset(495));
            assertTrue(controller.confirmWallpaperUpload(version)); drain();
        }
        assertEquals(1, subscription.events.size());
    }

    @Test public void stateCopiesIdsAndRejectsInvalidSourcesAndResources() {
        int[] ids = {361, 495};
        WallpaperController.State state = new WallpaperController.State("carousel", ids);
        ids[0] = 0;
        assertEquals("[361, 495]", state.getIdsJson());
        for (String source : Arrays.asList("preset", "custom", "carousel", "art_clock", "bad")) {
            try { new WallpaperController.State(source); fail(source); }
            catch (IllegalArgumentException expected) { }
        }
        try { new WallpaperController.State("preset", -1); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void nonResourceDesktopStatesClearPreviousWallpaperAndPendingUpload() throws Exception {
        RecordingSubscription subscription = subscribe();
        long token = controller.stageLocalWallpaper(preset(495));
        controller.reportAppliedWallpaper(preset(495));
        controller.reportAppliedWallpaper(WallpaperController.State.artClock(589, 1)); drain();
        assertFalse(controller.confirmWallpaperUpload(token));
        assertWallpaper(subscription.events.get(0), 0, "art_clock", "[]");
        assertClock(subscription.events.get(0), 589, 1);
        controller.reportAppliedWallpaper(new WallpaperController.State("follow_role")); drain();
        assertWallpaper(subscription.events.get(1), 0, "follow_role", "[]");
        assertClock(subscription.events.get(1), 0, 0);
    }

    @Test public void everyConfirmedClockSwitchNotifiesBothPhonesIncludingReturningToFirstClock() throws Exception {
        RecordingSubscription first = subscribe();
        RecordingSubscription second = subscribe();
        int[][] clocks = {{589, 1}, {588, 2}, {591, 3}, {589, 1}};
        for (int i = 0; i < clocks.length; i++) {
            WallpaperController.State state = WallpaperController.State.artClock(clocks[i][0], clocks[i][1]);
            assertEquals(7, state.getUploadType());
            assertEquals(clocks[i][0], state.getUploadId());
            long token = controller.stageLocalWallpaper(state);
            assertFalse(controller.confirmWallpaperUpload(token));
            assertEquals(token, controller.reportAppliedWallpaper(state));
            drain();
            assertEquals(i, first.events.size());
            assertEquals(i == 0 ? 0 : clocks[i - 1][0],
                    implementation.getClockId(new UnsignedIntegerFourBytes(0)).getValue().intValue());
            assertTrue(controller.confirmWallpaperUpload(token)); drain();
            assertWallpaper(first.events.get(i), 0, "art_clock", "[]");
            assertClock(first.events.get(i), clocks[i][0], clocks[i][1]);
            assertClock(second.events.get(i), clocks[i][0], clocks[i][1]);
        }
        assertEquals(Arrays.asList("0", "1", "2", "3", "4"), first.sequences);
        assertEquals(first.sequences, second.sequences);
    }

    @Test public void unconfirmedAndStaleClockUploadsKeepPreviousConfirmedSnapshot() throws Exception {
        WallpaperController.State original = WallpaperController.State.artClock(589, 1);
        controller.reportAppliedWallpaper(original); drain();
        RecordingSubscription subscription = subscribe();
        WallpaperController.State second = WallpaperController.State.artClock(588, 2);
        long oldToken = controller.stageLocalWallpaper(second);
        controller.reportAppliedWallpaper(second);
        controller.reportAppliedWallpaper(second); drain();
        assertTrue(subscription.events.isEmpty());
        assertClock(subscribe().initial, 589, 1);
        assertEquals(1, implementation.getClockStyle(new UnsignedIntegerFourBytes(0)).getValue().intValue());
        WallpaperController.State third = WallpaperController.State.artClock(591, 3);
        long latest = controller.stageLocalWallpaper(third);
        controller.reportAppliedWallpaper(third);
        assertFalse(controller.confirmWallpaperUpload(oldToken));
        assertTrue(controller.confirmWallpaperUpload(latest)); drain();
        assertEquals(1, subscription.events.size());
        assertClock(subscription.events.get(0), 591, 3);
        assertFalse(controller.confirmWallpaperUpload(latest));
    }

    @Test public void clockAndWallpaperUploadsInvalidateEachOther() throws Exception {
        RecordingSubscription subscription = subscribe();
        WallpaperController.State clock = WallpaperController.State.artClock(589, 1);
        long clockToken = controller.stageLocalWallpaper(clock);
        controller.reportAppliedWallpaper(clock);
        long wallpaperToken = controller.stageLocalWallpaper(preset(361));
        controller.reportAppliedWallpaper(preset(361));
        assertFalse(controller.confirmWallpaperUpload(clockToken));
        long finalClockToken = controller.stageLocalWallpaper(clock);
        controller.reportAppliedWallpaper(clock);
        assertFalse(controller.confirmWallpaperUpload(wallpaperToken));
        assertFalse(controller.confirmWallpaperUpload(clockToken));
        assertTrue(controller.confirmWallpaperUpload(finalClockToken)); drain();
        assertEquals(1, subscription.events.size());
        assertClock(subscription.events.get(0), 589, 1);
    }

    @Test public void duplicateClockIsSuppressedAndWallpaperResetsClockFieldsInSameEvent() throws Exception {
        RecordingSubscription subscription = subscribe();
        WallpaperController.State clock = WallpaperController.State.artClock(588, 2);
        for (int i = 0; i < 2; i++) {
            long token = controller.stageLocalWallpaper(clock);
            controller.reportAppliedWallpaper(clock);
            assertTrue(controller.confirmWallpaperUpload(token)); drain();
        }
        controller.reportAppliedWallpaper(clock); drain();
        assertEquals(1, subscription.events.size());
        assertClock(subscribe().initial, 588, 2);
        long token = controller.stageLocalWallpaper(preset(495));
        assertEquals(6, preset(495).getUploadType());
        assertEquals(495, preset(495).getUploadId());
        controller.reportAppliedWallpaper(preset(495));
        assertTrue(controller.confirmWallpaperUpload(token)); drain();
        assertEquals(2, subscription.events.size());
        assertWallpaper(subscription.events.get(1), 495, "preset", "[495]");
        assertClock(subscription.events.get(1), 0, 0);
        assertClock(subscribe().initial, 0, 0);
    }

    @Test public void clockStateRequiresPositiveResourceAndKnownStyle() {
        for (int[] invalid : new int[][]{{0, 1}, {-1, 1}, {589, 0}, {589, 4}}) {
            try { WallpaperController.State.artClock(invalid[0], invalid[1]); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void clockQueryActionsReturnConfirmedFieldsAndRejectInvalidInstances() throws Exception {
        controller.reportAppliedWallpaper(WallpaperController.State.artClock(591, 3)); drain();
        String[][] actions = {{"GetClockId", "CurrentClockId", "591"},
                {"GetClockStyle", "CurrentClockStyle", "3"}};
        for (String[] spec : actions) {
            ActionInvocation<LocalService> action = new ActionInvocation<>(service.getAction(spec[0]));
            action.setInput("InstanceID", new UnsignedIntegerFourBytes(0));
            service.getExecutor(action.getAction()).execute(action);
            assertNull(action.getFailure());
            assertEquals(spec[2], action.getOutput(spec[1]).toString());
            action = new ActionInvocation<>(service.getAction(spec[0]));
            action.setInput("InstanceID", new UnsignedIntegerFourBytes(1));
            service.getExecutor(action.getAction()).execute(action);
            assertEquals(702, action.getFailure().getErrorCode());
        }
    }

    @Test public void getActionReadsConfirmedIdAndRejectsInvalidInstance() throws Exception {
        controller.reportAppliedWallpaper(new WallpaperController.State("custom", Integer.MAX_VALUE)); drain();
        ActionInvocation<LocalService> action = new ActionInvocation<>(service.getAction("GetWallpaperId"));
        action.setInput("InstanceID", new UnsignedIntegerFourBytes(0));
        service.getExecutor(action.getAction()).execute(action);
        assertNull(action.getFailure());
        assertEquals("2147483647", action.getOutput("CurrentWallpaperId").toString());
        action = new ActionInvocation<>(service.getAction("GetWallpaperId"));
        action.setInput("InstanceID", new UnsignedIntegerFourBytes(1));
        service.getExecutor(action.getAction()).execute(action);
        assertEquals(702, action.getFailure().getErrorCode());
    }

    @Test public void closingServiceDropsQueuedEventsAndPreservesReplacementListener() throws Exception {
        RecordingSubscription subscription = subscribe();
        controller.reportAppliedWallpaper(preset(495));
        List<WallpaperController.State> replacement = new ArrayList<>();
        controller.setOnWallpaperChangedListener(replacement::add);
        implementation.close(); drain();
        assertTrue(subscription.events.isEmpty());
        controller.reportAppliedWallpaper(preset(496));
        assertEquals(Collections.singletonList(preset(496)), replacement);
    }

    private WallpaperController.State preset(int id) { return new WallpaperController.State("preset", id); }

    private RecordingSubscription subscribe() throws Exception {
        RecordingSubscription subscription = new RecordingSubscription(service);
        subscription.registerOnService(); subscription.establish();
        return subscription;
    }

    private void drain() throws Exception {
        while (!pending.isEmpty()) {
            Runnable event = pending.remove();
            service.getManager().execute(manager -> event.run());
        }
    }

    private static LastChange parse(OutgoingEventRequestMessage message) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        String xml = factory.newDocumentBuilder().parse(new InputSource(new StringReader(message.getBodyString())))
                .getElementsByTagName("LastChange").item(0).getTextContent();
        return new LastChange(new RenderingControlLastChangeParser(), xml);
    }

    private static void assertWallpaper(OutgoingEventRequestMessage message, int id, String source, String ids) throws Exception {
        LastChange change = parse(message);
        assertEquals(id, change.getEventedValue(0, RenderingControlVariable.WallpaperId.class).getValue().getValue().intValue());
        assertEquals(source, change.getEventedValue(0, RenderingControlVariable.WallpaperSource.class).getValue());
        assertEquals(ids, change.getEventedValue(0, RenderingControlVariable.WallpaperIds.class).getValue());
    }

    private static void assertClock(OutgoingEventRequestMessage message, int id, int style) throws Exception {
        LastChange change = parse(message);
        assertEquals(id, change.getEventedValue(0, RenderingControlVariable.ClockId.class).getValue().getValue().intValue());
        assertEquals(style, change.getEventedValue(0, RenderingControlVariable.ClockStyle.class).getValue().getValue().intValue());
    }

    private static class RecordingSubscription extends LocalGENASubscription {
        final List<OutgoingEventRequestMessage> events = new ArrayList<>();
        final List<String> sequences = new ArrayList<>();
        OutgoingEventRequestMessage initial;
        RecordingSubscription(LocalService service) throws Exception {
            super(service, 1800, Collections.singletonList(new URL("http://phone:8080/upnp/rendering")));
        }
        private OutgoingEventRequestMessage message() {
            OutgoingEventRequestMessage result = new OutgoingEventRequestMessage(this, getCallbackURLs().get(0));
            new GENAEventProcessorImpl().writeBody(result);
            sequences.add(result.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
            incrementSequence();
            return result;
        }
        @Override public void established() { initial = message(); }
        @Override public void ended(CancelReason reason) { }
        @Override public void eventReceived() { events.add(message()); }
    }
}

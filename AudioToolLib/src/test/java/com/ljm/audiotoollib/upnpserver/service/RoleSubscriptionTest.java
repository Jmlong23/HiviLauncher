package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.binding.annotations.AnnotationLocalServiceBinder;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.UDA10ServiceDescriptorBinderImpl;
import com.ljm.audiotoollib.upnpserver.cling.model.action.ActionInvocation;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.CancelReason;
import com.ljm.audiotoollib.upnpserver.cling.model.gena.LocalGENASubscription;
import com.ljm.audiotoollib.upnpserver.cling.model.message.gena.OutgoingEventRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.StreamRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.StreamResponseMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.UpnpRequest;
import com.ljm.audiotoollib.upnpserver.cling.model.message.UpnpResponse;
import com.ljm.audiotoollib.upnpserver.cling.mock.MockRouter;
import com.ljm.audiotoollib.upnpserver.cling.mock.MockUpnpService;
import com.ljm.audiotoollib.upnpserver.cling.mock.MockUpnpServiceConfiguration;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingSubscribe;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.ReceivingUnsubscribe;
import com.ljm.audiotoollib.upnpserver.cling.protocol.sync.SendingEvent;
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
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlException;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.lastchange.RenderingControlVariable;
import com.ljm.audiotoollib.upnpserver.cling.transport.impl.GENAEventProcessorImpl;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.seamless.xml.ParserException;

import java.io.StringReader;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RoleSubscriptionTest {
    private RoleController controller;
    private AudioRenderServiceImpl implementation;
    private LocalService<AudioRenderServiceImpl> service;
    private LastChangeAwareServiceManager<AudioRenderServiceImpl> serviceManager;
    private final Queue<Runnable> pendingEvents = new ArrayDeque<>();

    @Before
    public void setUp() throws Exception {
        controller = new RoleController();
        implementation = new AudioRenderServiceImpl(new LastChange(new RenderingControlLastChangeParser()),
                new RenderControlManager(), controller, pendingEvents::add) {
            @Override
            public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
                return new UnsignedIntegerFourBytes[]{getDefaultInstanceID()};
            }

            @Override
            public UnsignedIntegerTwoBytes getVolume(UnsignedIntegerFourBytes instanceId, String channel) {
                return new UnsignedIntegerTwoBytes(50);
            }

            @Override
            public boolean getMute(UnsignedIntegerFourBytes instanceId, String channel) {
                return false;
            }
        };
        service = new AnnotationLocalServiceBinder().read(AudioRenderServiceImpl.class);
        new LocalDevice(new DeviceIdentity(UDN.uniqueSystemIdentifier("role-subscription-test")),
                new UDADeviceType("MediaRenderer", 1), new DeviceDetails("Role test speaker"), service);
        serviceManager = new LastChangeAwareServiceManager<AudioRenderServiceImpl>(service,
                new RenderingControlLastChangeParser()) {
            @Override
            protected AudioRenderServiceImpl createServiceInstance() {
                return implementation;
            }
        };
        service.setManager(serviceManager);
        serviceManager.getImplementation();
    }

    @Test
    public void roleNumbersMatchExistingDialogOrderAndRoundTrip() {
        int[] characterIndexes = new int[]{-1, 0, 3, 1, 2};
        for (int roleId = 1; roleId <= 5; roleId++) {
            int characterIndex = RoleController.characterIndexForRoleId(roleId);
            assertEquals(characterIndexes[roleId - 1], characterIndex);
            assertEquals(roleId, RoleController.roleIdForCharacterSelection(
                    characterIndex < 0 ? 0 : characterIndex, roleId != 1));
        }
        assertEquals(1, RoleController.roleIdForCharacterSelection(2, false));
        assertEquals(4, RoleController.roleIdForCharacterSelection(99, true));
    }

    @Test
    public void descriptorAdvertisesRoleActionsAndNumericRange() throws Exception {
        assertNotNull(service.getAction("GetRoleId"));
        assertNotNull(service.getAction("SetRoleId"));
        assertFalse(service.getStateVariable("RoleId").getEventDetails().isSendEvents());
        String descriptor = new UDA10ServiceDescriptorBinderImpl().generate(service);
        assertTrue(descriptor.contains("<name>RoleId</name>"));
        assertTrue(descriptor.contains("<minimum>1</minimum>"));
        assertTrue(descriptor.contains("<maximum>5</maximum>"));
    }

    @Test
    public void initialNotifyContainsSavedRoleAndEscapedRcsSnapshot() throws Exception {
        controller.reportRoleId(5);
        drainEvents();
        RecordingSubscription subscription = subscribe();
        assertEquals(5, roleId(subscription.initialMessage));
        assertEquals("0", subscription.initialMessage.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
        assertEquals("upnp:event", subscription.initialMessage.getHeaders().getFirstHeaderString(UpnpHeader.Type.NT));
        assertEquals("upnp:propchange", subscription.initialMessage.getHeaders().getFirstHeaderString(UpnpHeader.Type.NTS));
        assertEquals(subscription.getSubscriptionId(),
                subscription.initialMessage.getHeaders().getFirstHeaderString(UpnpHeader.Type.SID));
        assertTrue(subscription.initialMessage.getBodyString().contains("&lt;RoleId"));
        Document event = parseXml(lastChange(subscription.initialMessage));
        assertEquals(RenderingControlLastChangeParser.NAMESPACE_URI, event.getDocumentElement().getNamespaceURI());
        assertEquals("0", event.getElementsByTagNameNS(
                RenderingControlLastChangeParser.NAMESPACE_URI, "InstanceID").item(0)
                .getAttributes().getNamedItem("val").getNodeValue());
        assertNotNull(event.getElementsByTagNameNS(
                RenderingControlLastChangeParser.NAMESPACE_URI, "Volume").item(0));
    }

    @Test
    public void rapidSwitchesNotifyBothPhonesWithIndependentSequences() throws Exception {
        RecordingSubscription first = subscribe();
        RecordingSubscription second = subscribe();
        for (int roleId : new int[]{2, 3, 4, 5, 1}) {
            controller.reportRoleId(roleId);
        }
        drainEvents();
        assertEquals(Arrays.asList(2, 3, 4, 5, 1), first.roles);
        assertEquals(first.roles, second.roles);
        assertEquals(Arrays.asList("1", "2", "3", "4", "5"), first.sequences);
        assertFalse(first.getSubscriptionId().equals(second.getSubscriptionId()));
        assertEquals(0, service.getStateVariable("LastChange").getEventDetails().getEventMaximumRateMilliseconds());
    }

    @Test
    public void repeatedRoleDoesNotEmitDuplicateEvent() throws Exception {
        RecordingSubscription subscription = subscribe();
        controller.reportRoleId(2);
        controller.reportRoleId(2);
        drainEvents();
        assertEquals(Collections.singletonList(2), subscription.roles);
    }

    @Test
    public void localSelectionIsQueryableButNotNotifiedUntilUploadConfirmed() throws Exception {
        RecordingSubscription subscription = subscribe();
        long version = controller.stageRoleId(5);
        assertEquals(5, implementation.getRoleId(new UnsignedIntegerFourBytes(0)).getValue().intValue());
        drainEvents();
        assertTrue(subscription.roles.isEmpty());
        assertTrue(controller.confirmRoleUpload(version));
        drainEvents();
        assertEquals(Collections.singletonList(5), subscription.roles);
        assertFalse(controller.confirmRoleUpload(version));
        drainEvents();
        assertEquals(Collections.singletonList("1"), subscription.sequences);
    }

    @Test
    public void failedUploadLeavesLocalRoleWithoutChangeNotification() throws Exception {
        RecordingSubscription subscription = subscribe();
        long version = controller.stageRoleId(3);
        // A transport/business failure never calls confirmRoleUpload.
        drainEvents();
        assertEquals(3, controller.getRoleId());
        assertTrue(controller.isPendingRoleChange(version));
        assertTrue(subscription.roles.isEmpty());
    }

    @Test
    public void oldUploadCannotConfirmNewerSelectionEvenWhenRoleRepeats() throws Exception {
        RecordingSubscription subscription = subscribe();
        long first = controller.stageRoleId(5);
        long second = controller.stageRoleId(2);
        long latest = controller.stageRoleId(5);
        assertFalse(controller.isPendingRoleChange(first));
        assertFalse(controller.confirmRoleUpload(first));
        assertFalse(controller.confirmRoleUpload(second));
        drainEvents();
        assertTrue(subscription.roles.isEmpty());
        assertTrue(controller.confirmRoleUpload(latest));
        drainEvents();
        assertEquals(Collections.singletonList(5), subscription.roles);
    }

    @Test
    public void retryingUnconfirmedRolePublishesAfterSuccessfulUpload() throws Exception {
        RecordingSubscription subscription = subscribe();
        long failed = controller.stageRoleId(5);
        long retry = controller.stageRoleId(5);
        assertFalse(controller.confirmRoleUpload(failed));
        assertTrue(controller.confirmRoleUpload(retry));
        drainEvents();
        assertEquals(Collections.singletonList(5), subscription.roles);
    }

    @Test
    public void matchingCloudEchoDoesNotBypassUploadConfirmation() throws Exception {
        RecordingSubscription subscription = subscribe();
        long version = controller.stageRoleId(4);
        controller.reportRoleId(4);
        drainEvents();
        assertTrue(subscription.roles.isEmpty());
        assertTrue(controller.confirmRoleUpload(version));
        drainEvents();
        assertEquals(Collections.singletonList(4), subscription.roles);
    }

    @Test
    public void differentCloudSelectionInvalidatesPendingLocalUpload() throws Exception {
        RecordingSubscription subscription = subscribe();
        long version = controller.stageRoleId(5);
        controller.reportRoleId(3);
        assertFalse(controller.confirmRoleUpload(version));
        drainEvents();
        assertEquals(3, controller.getRoleId());
        assertEquals(Collections.singletonList(3), subscription.roles);
    }

    @Test
    public void consecutiveSuccessfulUploadsNotifyLive2dAndDefaultRoles() throws Exception {
        RecordingSubscription subscription = subscribe();
        for (int roleId : new int[]{5, 1}) {
            long version = controller.stageRoleId(roleId);
            assertTrue(controller.confirmRoleUpload(version));
            drainEvents();
        }
        assertEquals(Arrays.asList(5, 1), subscription.roles);
        assertEquals(Arrays.asList("1", "2"), subscription.sequences);
    }

    @Test
    public void setActionWaitsForCommittedSelectionBeforeNotifying() throws Exception {
        RecordingSubscription subscription = subscribe();
        List<Integer> requestedRoles = new ArrayList<>();
        controller.setOnRoleChangeRequestedListener(requestedRoles::add);
        ActionInvocation<LocalService> action = invokeSetRole(3);
        assertNull(action.getFailure());
        assertEquals(Collections.singletonList(3), requestedRoles);
        assertEquals(1, implementation.getRoleId(new UnsignedIntegerFourBytes(0)).getValue().intValue());
        drainEvents();
        assertTrue(subscription.roles.isEmpty());
        controller.reportRoleId(3);
        drainEvents();
        assertEquals(Collections.singletonList(3), subscription.roles);
    }

    @Test
    public void getActionReturnsRoleUsedBySubscription() throws Exception {
        controller.reportRoleId(4);
        ActionInvocation<LocalService> action = new ActionInvocation<>(service.getAction("GetRoleId"));
        action.setInput("InstanceID", new UnsignedIntegerFourBytes(0));
        service.getExecutor(action.getAction()).execute(action);
        assertNull(action.getFailure());
        assertEquals("4", action.getOutput("CurrentRoleId").toString());
    }

    @Test
    public void invalidRoleAndUnavailableHandlerFailWithoutChangingState() throws Exception {
        for (int roleId : new int[]{0, 6}) {
            assertEquals(402, invokeSetRole(roleId).getFailure().getErrorCode());
        }
        assertEquals(501, invokeSetRole(2).getFailure().getErrorCode());
        assertEquals(1, controller.getRoleId());
        assertTrue(pendingEvents.isEmpty());
    }

    @Test
    public void unknownInstanceFailsWithRenderingControlError() throws Exception {
        try {
            implementation.getRoleId(new UnsignedIntegerFourBytes(1));
            fail("Expected invalid instance");
        } catch (RenderingControlException exception) {
            assertEquals(702, exception.getErrorCode());
        }
    }

    @Test
    public void resourceFailureDoesNotPublishRequestedRole() throws Exception {
        RecordingSubscription subscription = subscribe();
        controller.setOnRoleChangeRequestedListener(roleId -> {
            throw new RenderingControlException(501, "Character resources are not ready");
        });
        assertEquals(501, invokeSetRole(4).getFailure().getErrorCode());
        drainEvents();
        assertEquals(1, controller.getRoleId());
        assertTrue(subscription.roles.isEmpty());
    }

    @Test
    public void unsubscribingOnePhoneLeavesOtherPhoneActive() throws Exception {
        RecordingSubscription first = subscribe();
        RecordingSubscription second = subscribe();
        first.end(null);
        controller.reportRoleId(3);
        drainEvents();
        assertTrue(first.roles.isEmpty());
        assertEquals(Collections.singletonList(3), second.roles);
        second.setSubscriptionDuration(600);
        assertEquals(600, second.getActualDurationSeconds());
    }

    @Test
    public void serviceCloseDropsPendingEventsWithoutClearingReplacementListener() throws Exception {
        RecordingSubscription subscription = subscribe();
        controller.reportRoleId(2);
        List<Integer> replacementRoles = new ArrayList<>();
        controller.setOnRoleChangedListener(replacementRoles::add);
        implementation.close();
        drainEvents();
        controller.reportRoleId(3);
        assertTrue(subscription.roles.isEmpty());
        assertEquals(Collections.singletonList(3), replacementRoles);
    }

    @Test
    public void clearingOldRequestHandlerPreservesReplacement() throws Exception {
        RoleController.OnRoleChangeRequestedListener oldListener = roleId -> { };
        List<Integer> requestedRoles = new ArrayList<>();
        controller.setOnRoleChangeRequestedListener(requestedRoles::add);
        controller.clearOnRoleChangeRequestedListener(oldListener);
        controller.requestRoleId(5);
        assertEquals(Collections.singletonList(5), requestedRoles);
    }

    @Test
    public void extendedSchemaRejectsOutOfRangeAndMissingRoleValues() throws Exception {
        for (String attributes : new String[]{"val=\"0\"", "val=\"6\"", "val=\"abc\"", ""}) {
            try {
                new LastChange(new RenderingControlLastChangeParser(),
                        "<Event xmlns=\"" + RenderingControlLastChangeParser.NAMESPACE_URI + "\">"
                                + "<InstanceID val=\"0\"><RoleId " + attributes + "/></InstanceID></Event>");
                fail("Expected invalid RoleId XML to be rejected: " + attributes);
            } catch (ParserException expected) {
                assertNotNull(expected.getCause());
            }
        }
    }

    @Test
    public void extendedSchemaStillParsesStandardVolumeAndMuteValues() throws Exception {
        LastChange change = new LastChange(new RenderingControlLastChangeParser(),
                "<Event xmlns=\"" + RenderingControlLastChangeParser.NAMESPACE_URI + "\">"
                        + "<InstanceID val=\"0\"><Volume channel=\"Master\" val=\"50\"/>"
                        + "<Mute channel=\"Master\" val=\"0\"/><RoleId val=\"2\"/></InstanceID></Event>");
        assertEquals(50, change.getEventedValue(0, RenderingControlVariable.Volume.class)
                .getValue().getVolume().intValue());
        assertFalse(change.getEventedValue(0, RenderingControlVariable.Mute.class).getValue().getMute());
        assertEquals(2, change.getEventedValue(0, RenderingControlVariable.RoleId.class)
                .getValue().getValue().intValue());
    }

    @Test
    public void protocolHandshakeSendsInitialAndRoleEventsThenRenewsAndUnsubscribes() throws Exception {
        MockUpnpService stack = protocolStack(new StreamResponseMessage(UpnpResponse.Status.OK));
        try {
            ReceivingSubscribe subscribe = new ReceivingSubscribe(stack, subscriptionRequest(stack));
            subscribe.run();
            StreamResponseMessage response = subscribe.getOutputMessage();
            assertEquals(200, response.getOperation().getStatusCode());
            assertTrue(stack.getRouter().getSentStreamRequestMessages().isEmpty());
            subscribe.responseSent(response);

            List<StreamRequestMessage> sent = stack.getRouter().getSentStreamRequestMessages();
            assertEquals(1, sent.size());
            OutgoingEventRequestMessage initial = (OutgoingEventRequestMessage) sent.get(0);
            assertEquals(UpnpRequest.Method.NOTIFY, initial.getOperation().getMethod());
            assertEquals("http://phone:8080/dlna/callback/rendering", initial.getUri().toString());
            assertEquals("0", initial.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
            assertEquals(1, roleId(initial));

            controller.reportRoleId(4);
            drainEvents();
            assertEquals(2, sent.size());
            OutgoingEventRequestMessage changed = (OutgoingEventRequestMessage) sent.get(1);
            assertEquals("1", changed.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
            assertEquals(4, roleId(changed));
            String sid = initial.getHeaders().getFirstHeaderString(UpnpHeader.Type.SID);
            assertEquals(sid, changed.getHeaders().getFirstHeaderString(UpnpHeader.Type.SID));

            StreamRequestMessage renewRequest = new StreamRequestMessage(UpnpRequest.Method.SUBSCRIBE,
                    stack.getConfiguration().getNamespace().getEventSubscriptionPath(service));
            renewRequest.getHeaders().add("SID", sid);
            renewRequest.getHeaders().add("TIMEOUT", "Second-600");
            ReceivingSubscribe renew = new ReceivingSubscribe(stack, renewRequest);
            renew.run();
            assertEquals(200, renew.getOutputMessage().getOperation().getStatusCode());
            renew.responseSent(renew.getOutputMessage());
            assertEquals(600, stack.getRegistry().getLocalSubscription(sid).getActualDurationSeconds());
            assertEquals(2, sent.size());

            StreamRequestMessage endRequest = new StreamRequestMessage(UpnpRequest.Method.UNSUBSCRIBE,
                    renewRequest.getUri());
            endRequest.getHeaders().add("SID", sid);
            ReceivingUnsubscribe end = new ReceivingUnsubscribe(stack, endRequest);
            end.run();
            assertEquals(200, end.getOutputMessage().getOperation().getStatusCode());
            assertNull(stack.getRegistry().getLocalSubscription(sid));
            controller.reportRoleId(2);
            drainEvents();
            assertEquals(2, sent.size());
        } finally {
            stack.shutdown();
        }
    }

    @Test
    public void sendingEventKeepsSequenceSnapshotAfterSubscriptionIncrements() throws Exception {
        MockUpnpService stack = protocolStack(new StreamResponseMessage(UpnpResponse.Status.OK));
        try {
            RecordingSubscription subscription = subscribe();
            class SequenceEvent extends SendingEvent {
                SequenceEvent() { super(stack, subscription); }
                long sequence() { return currentSequence.getValue(); }
            }
            SequenceEvent event = new SequenceEvent();
            assertEquals(1L, event.sequence());
            assertEquals(2L, subscription.getCurrentSequence().getValue().longValue());
            event.run();
            assertEquals("1", stack.getRouter().getSentStreamRequestMessages().get(0)
                    .getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
            subscription.end(null);
        } finally {
            stack.shutdown();
        }
    }

    @Test
    public void failedCallbackResponseDoesNotPreventLaterRoleEventDispatch() throws Exception {
        for (StreamResponseMessage reply : Arrays.asList(null,
                new StreamResponseMessage(UpnpResponse.Status.PRECONDITION_FAILED))) {
            MockUpnpService stack = protocolStack(reply);
            try {
                ReceivingSubscribe subscribe = new ReceivingSubscribe(stack, subscriptionRequest(stack));
                subscribe.run();
                subscribe.responseSent(subscribe.getOutputMessage());
                controller.reportRoleId(controller.getRoleId() == 4 ? 2 : 4);
                drainEvents();
                assertEquals(2, stack.getRouter().getSentStreamRequestMessages().size());
            } finally {
                stack.shutdown();
            }
        }
    }

    private MockUpnpService protocolStack(StreamResponseMessage callbackResponse) throws Exception {
        MockUpnpService stack = new MockUpnpService(new MockUpnpServiceConfiguration() {
            @Override public Executor getAsyncProtocolExecutor() { return Runnable::run; }
            @Override public ExecutorService getSyncProtocolExecutorService() { return getDefaultExecutorService(); }
        }) {
            @Override
            protected MockRouter createRouter() {
                return new MockRouter(getConfiguration(), getProtocolFactory()) {
                    @Override
                    public StreamResponseMessage getStreamResponseMessage(StreamRequestMessage message) {
                        return callbackResponse;
                    }
                };
            }
        };
        stack.getRegistry().addDevice(service.getDevice());
        return stack;
    }

    private StreamRequestMessage subscriptionRequest(MockUpnpService stack) {
        StreamRequestMessage request = new StreamRequestMessage(UpnpRequest.Method.SUBSCRIBE,
                stack.getConfiguration().getNamespace().getEventSubscriptionPath(service));
        request.getHeaders().add("CALLBACK", "<http://phone:8080/dlna/callback/rendering>");
        request.getHeaders().add("NT", "upnp:event");
        request.getHeaders().add("TIMEOUT", "Second-120");
        return request;
    }

    private ActionInvocation<LocalService> invokeSetRole(int roleId) throws Exception {
        ActionInvocation<LocalService> action = new ActionInvocation<>(service.getAction("SetRoleId"));
        action.setInput("InstanceID", new UnsignedIntegerFourBytes(0));
        action.setInput("DesiredRoleId", new UnsignedIntegerTwoBytes(roleId));
        service.getExecutor(action.getAction()).execute(action);
        return action;
    }

    private RecordingSubscription subscribe() throws Exception {
        RecordingSubscription subscription = new RecordingSubscription(service);
        subscription.registerOnService();
        subscription.establish();
        return subscription;
    }

    private void drainEvents() throws Exception {
        while (!pendingEvents.isEmpty()) {
            Runnable event = pendingEvents.remove();
            serviceManager.execute(manager -> event.run());
        }
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String lastChange(OutgoingEventRequestMessage message) throws Exception {
        return parseXml(message.getBodyString()).getElementsByTagName("LastChange").item(0).getTextContent();
    }

    private static int roleId(OutgoingEventRequestMessage message) throws Exception {
        LastChange change = new LastChange(new RenderingControlLastChangeParser(), lastChange(message));
        return change.getEventedValue(0, RenderingControlVariable.RoleId.class).getValue().getValue().intValue();
    }

    private static class RecordingSubscription extends LocalGENASubscription {
        final List<Integer> roles = new ArrayList<>();
        final List<String> sequences = new ArrayList<>();
        OutgoingEventRequestMessage initialMessage;

        RecordingSubscription(LocalService service) throws Exception {
            super(service, 1800, Collections.singletonList(new URL("http://phone:8080/dlna/callback/rendering")));
        }

        private OutgoingEventRequestMessage createMessage() {
            OutgoingEventRequestMessage message = new OutgoingEventRequestMessage(this, getCallbackURLs().get(0));
            new GENAEventProcessorImpl().writeBody(message);
            incrementSequence();
            return message;
        }

        @Override
        public void established() {
            initialMessage = createMessage();
        }

        @Override
        public void ended(CancelReason reason) { }

        @Override
        public void eventReceived() {
            OutgoingEventRequestMessage message = createMessage();
            try {
                roles.add(roleId(message));
                sequences.add(message.getHeaders().getFirstHeaderString(UpnpHeader.Type.SEQ));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }
}

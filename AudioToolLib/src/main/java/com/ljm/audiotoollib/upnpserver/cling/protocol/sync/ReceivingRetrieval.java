/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.ljm.audiotoollib.upnpserver.cling.protocol.sync;

import com.ljm.audiotoollib.upnpserver.cling.UpnpService;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.DescriptorBindingException;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.DeviceDescriptorBinder;
import com.ljm.audiotoollib.upnpserver.cling.binding.xml.ServiceDescriptorBinder;
import com.ljm.audiotoollib.upnpserver.cling.model.message.StreamRequestMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.StreamResponseMessage;
import com.ljm.audiotoollib.upnpserver.cling.model.message.UpnpResponse;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.ContentTypeHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.ServerHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.message.header.UpnpHeader;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.Icon;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalDevice;
import com.ljm.audiotoollib.upnpserver.cling.model.meta.LocalService;
import com.ljm.audiotoollib.upnpserver.cling.model.resource.DeviceDescriptorResource;
import com.ljm.audiotoollib.upnpserver.cling.model.resource.IconResource;
import com.ljm.audiotoollib.upnpserver.cling.model.resource.Resource;
import com.ljm.audiotoollib.upnpserver.cling.model.resource.ServiceDescriptorResource;
import com.ljm.audiotoollib.upnpserver.cling.protocol.ReceivingSync;
import com.ljm.audiotoollib.upnpserver.cling.registry.Registry;
import com.ljm.audiotoollib.upnpserver.cling.transport.RouterException;

import org.seamless.util.Exceptions;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles reception of device/service descriptor and icon retrieval messages.
 *
 * <p>
 * Requested device and service XML descriptors are generated on-the-fly for every request.
 * </p>
 * <p>
 * Descriptor XML is dynamically generated depending on the control point - some control
 * points require different metadata than others for the same device and services.
 * </p>
 *
 * @author Christian Bauer
 */
public class ReceivingRetrieval extends ReceivingSync<StreamRequestMessage, StreamResponseMessage> {

    final private static Logger log = Logger.getLogger(ReceivingRetrieval.class.getName());

    /** 缓存设备/服务描述 XML，降低 char[] 分配；短 TTL 不影响手机轮询播放状态 */
    private static final ConcurrentHashMap<String, CachedDescriptor> DESCRIPTOR_CACHE =
            new ConcurrentHashMap<>();

    private static final class CachedDescriptor {
        final byte[] body;

        CachedDescriptor(byte[] body) {
            this.body = body;
        }
    }

    public ReceivingRetrieval(UpnpService upnpService, StreamRequestMessage inputMessage) {
        super(upnpService, inputMessage);
    }

    protected StreamResponseMessage executeSync() throws RouterException {

        if (!getInputMessage().hasHostHeader()) {
            log.fine("Ignoring message, missing HOST header: " + getInputMessage());
            return new StreamResponseMessage(new UpnpResponse(UpnpResponse.Status.PRECONDITION_FAILED));
        }

        URI requestedURI = getInputMessage().getOperation().getURI();

        Resource foundResource = getUpnpService().getRegistry().getResource(requestedURI);

        if (foundResource == null) {
            foundResource = onResourceNotFound(requestedURI);
            if (foundResource == null) {
                log.fine("No local resource found: " + getInputMessage());
                return null;
            }
        }

        return createResponse(requestedURI, foundResource);
    }

    protected StreamResponseMessage createResponse(URI requestedURI, Resource resource) {

        StreamResponseMessage response;

        try {

            if (DeviceDescriptorResource.class.isAssignableFrom(resource.getClass())) {

                log.fine("Found local device matching relative request URI: " + requestedURI);
                LocalDevice device = (LocalDevice) resource.getModel();

                String cacheKey = "dev:" + requestedURI;
                byte[] deviceDescriptor = getCachedDescriptor(cacheKey);
                if (deviceDescriptor == null) {
                    DeviceDescriptorBinder deviceDescriptorBinder =
                            getUpnpService().getConfiguration().getDeviceDescriptorBinderUDA10();
                    deviceDescriptor = toUtf8Bytes(deviceDescriptorBinder.generate(
                            device,
                            getRemoteClientInfo(),
                            getUpnpService().getConfiguration().getNamespace()
                    ));
                    putCachedDescriptor(cacheKey, deviceDescriptor);
                }
                response = new StreamResponseMessage(
                        deviceDescriptor,
                        new ContentTypeHeader(ContentTypeHeader.DEFAULT_CONTENT_TYPE)
                );
            } else if (ServiceDescriptorResource.class.isAssignableFrom(resource.getClass())) {


                log.fine("Found local service matching relative request URI: " + requestedURI);
                LocalService service = (LocalService) resource.getModel();

                String cacheKey = "svc:" + requestedURI;
                byte[] serviceDescriptor = getCachedDescriptor(cacheKey);
                if (serviceDescriptor == null) {
                    ServiceDescriptorBinder serviceDescriptorBinder =
                            getUpnpService().getConfiguration().getServiceDescriptorBinderUDA10();
                    serviceDescriptor = toUtf8Bytes(serviceDescriptorBinder.generate(service));
                    putCachedDescriptor(cacheKey, serviceDescriptor);
                }
                response = new StreamResponseMessage(
                        serviceDescriptor,
                        new ContentTypeHeader(ContentTypeHeader.DEFAULT_CONTENT_TYPE)
                );

            } else if (IconResource.class.isAssignableFrom(resource.getClass())) {

                log.fine("Found local icon matching relative request URI: " + requestedURI);
                Icon icon = (Icon) resource.getModel();
                response = new StreamResponseMessage(icon.getData(), icon.getMimeType());

            } else {

                log.fine("Ignoring GET for found local resource: " + resource);
                return null;
            }

        } catch (DescriptorBindingException ex) {
            log.warning("Error generating requested device/service descriptor: " + ex.toString());
            log.log(Level.WARNING, "Exception root cause: ", Exceptions.unwrap(ex));
            response = new StreamResponseMessage(UpnpResponse.Status.INTERNAL_SERVER_ERROR);
        }
        
        response.getHeaders().add(UpnpHeader.Type.SERVER, new ServerHeader());

        return response;
    }

    /**
     * Called if the {@link Registry} had no result.
     *
     * @param requestedURIPath The requested URI path
     * @return <code>null</code> or your own {@link Resource}
     */
    protected Resource onResourceNotFound(URI requestedURIPath) {
        return null;
    }

    public static void clearDescriptorCache() {
        DESCRIPTOR_CACHE.clear();
    }

    private static byte[] getCachedDescriptor(String cacheKey) {
        CachedDescriptor cached = DESCRIPTOR_CACHE.get(cacheKey);
        return cached != null ? cached.body : null;
    }

    private static void putCachedDescriptor(String cacheKey, byte[] body) {
        if (body == null || body.length == 0) {
            return;
        }
        DESCRIPTOR_CACHE.put(cacheKey, new CachedDescriptor(body));
    }

    private static byte[] toUtf8Bytes(String body) {
        return body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
    }
}

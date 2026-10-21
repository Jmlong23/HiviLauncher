package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.EventedValue;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.LastChange;

import java.beans.PropertyChangeSupport;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/** Called under the owning service manager lock; unchanged fields never produce traffic. */
final class StateEventPublisher {
    private final Map<Class<?>, List<Map.Entry<String, String>>> previous = new HashMap<>();

    void publish(LastChange lastChange, PropertyChangeSupport support, EventedValue... values) {
        for (EventedValue value : values) {
            List<Map.Entry<String, String>> signature = Arrays.asList(value.getAttributes());
            if (!signature.equals(previous.get(value.getClass()))) {
                lastChange.setEventedValue(0, value);
                previous.put(value.getClass(), signature);
            }
        }
        lastChange.fire(support);
    }
}

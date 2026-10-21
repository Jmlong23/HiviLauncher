package com.ljm.audiotoollib.upnpserver.entity;

import com.ljm.audiotoollib.upnpserver.cling.model.types.UnsignedIntegerFourBytes;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.EventedValue;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.EventedValueString;
import com.ljm.audiotoollib.upnpserver.cling.support.lastchange.EventedValueUnsignedIntegerFourBytes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PlayQueueVariable {
    public static final Set<Class<? extends EventedValue>> ALL = new HashSet<>(Arrays.asList(
            CurrentPlayListName.class, LoopMode.class, CurrentIndex.class));

    public static class CurrentPlayListName extends EventedValueString {
        public CurrentPlayListName(String value) { super(value); }
        public CurrentPlayListName(Map.Entry<String, String>[] attributes) { super(attributes); }
    }

    public static class LoopMode extends EventedValueUnsignedIntegerFourBytes {
        public LoopMode(UnsignedIntegerFourBytes value) { super(value); }
        public LoopMode(Map.Entry<String, String>[] attributes) { super(attributes); }
    }

    public static class CurrentIndex extends EventedValueUnsignedIntegerFourBytes {
        public CurrentIndex(UnsignedIntegerFourBytes value) { super(value); }
        public CurrentIndex(Map.Entry<String, String>[] attributes) { super(attributes); }
    }
}

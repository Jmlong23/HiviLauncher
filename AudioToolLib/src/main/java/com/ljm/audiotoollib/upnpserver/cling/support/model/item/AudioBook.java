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

package com.ljm.audiotoollib.upnpserver.cling.support.model.item;

import com.ljm.audiotoollib.upnpserver.cling.support.model.DIDLObject;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Person;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Res;
import com.ljm.audiotoollib.upnpserver.cling.support.model.StorageMedium;
import com.ljm.audiotoollib.upnpserver.cling.support.model.container.Container;

import java.util.List;

/**
 * @author Christian Bauer
 */
public class AudioBook extends AudioItem {

    public static final DIDLObject.Class CLASS = new DIDLObject.Class("object.item.audioItem.audioBook");

    public AudioBook() {
        setClazz(CLASS);
    }

    public AudioBook(Item other) {
        super(other);
    }

    public AudioBook(String id, Container parent, String title, String creator, Res... resource) {
        this(id, parent.getId(), title, creator, null, null, null, resource);
    }

    public AudioBook(String id, Container parent, String title, String creator, String producer, String contributor, String date, Res... resource) {
        this(id, parent.getId(), title, creator, new Person(producer), new Person(contributor), date, resource);
    }

    public AudioBook(String id, String parentID, String title, String creator, Person producer, Person contributor, String date, Res... resource) {
        super(id, parentID, title, creator, resource);
        setClazz(CLASS);
        if (producer != null)
            addProperty(new DIDLObject.Property.UPNP.PRODUCER(producer));
        if (contributor != null)
            addProperty(new DIDLObject.Property.DC.CONTRIBUTOR(contributor));
        if (date != null)
            setDate(date);
    }
    
    public StorageMedium getStorageMedium() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.STORAGE_MEDIUM.class);
    }

    public AudioBook setStorageMedium(StorageMedium storageMedium) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.STORAGE_MEDIUM(storageMedium));
        return this;
    }

    public Person getFirstProducer() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.PRODUCER.class);
    }

    public Person[] getProducers() {
        List<Person> list = getPropertyValues(DIDLObject.Property.UPNP.PRODUCER.class);
        return list.toArray(new Person[list.size()]);
    }

    public AudioBook setProducers(Person[] persons) {
        removeProperties(DIDLObject.Property.UPNP.PRODUCER.class);
        for (Person p : persons) {
            addProperty(new DIDLObject.Property.UPNP.PRODUCER(p));
        }
        return this;
    }

    public Person getFirstContributor() {
        return getFirstPropertyValue(DIDLObject.Property.DC.CONTRIBUTOR.class);
    }

    public Person[] getContributors() {
        List<Person> list = getPropertyValues(DIDLObject.Property.DC.CONTRIBUTOR.class);
        return list.toArray(new Person[list.size()]);
    }

    public AudioBook setContributors(Person[] contributors) {
        removeProperties(DIDLObject.Property.DC.CONTRIBUTOR.class);
        for (Person p : contributors) {
            addProperty(new DIDLObject.Property.DC.CONTRIBUTOR(p));
        }
        return this;
    }

    public String getDate() {
        return getFirstPropertyValue(DIDLObject.Property.DC.DATE.class);
    }

    public AudioBook setDate(String date) {
        replaceFirstProperty(new DIDLObject.Property.DC.DATE(date));
        return this;
    }

}

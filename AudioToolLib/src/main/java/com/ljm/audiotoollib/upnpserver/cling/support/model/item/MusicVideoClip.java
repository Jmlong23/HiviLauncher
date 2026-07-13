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
import com.ljm.audiotoollib.upnpserver.cling.support.model.PersonWithRole;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Res;
import com.ljm.audiotoollib.upnpserver.cling.support.model.StorageMedium;
import com.ljm.audiotoollib.upnpserver.cling.support.model.container.Container;

import java.util.List;

/**
 * @author Christian Bauer
 */
public class MusicVideoClip extends VideoItem {

    public static final DIDLObject.Class CLASS = new DIDLObject.Class("object.item.videoItem.musicVideoClip");

    public MusicVideoClip() {
        setClazz(CLASS);
    }

    public MusicVideoClip(Item other) {
        super(other);
    }

    public MusicVideoClip(String id, Container parent, String title, String creator, Res... resource) {
        this(id, parent.getId(), title, creator, resource);
    }

    public MusicVideoClip(String id, String parentID, String title, String creator, Res... resource) {
        super(id, parentID, title, creator, resource);
        setClazz(CLASS);
    }

    public PersonWithRole getFirstArtist() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.ARTIST.class);
    }

    public PersonWithRole[] getArtists() {
        List<PersonWithRole> list = getPropertyValues(DIDLObject.Property.UPNP.ARTIST.class);
        return list.toArray(new PersonWithRole[list.size()]);
    }

    public MusicVideoClip setArtists(PersonWithRole[] artists) {
        removeProperties(DIDLObject.Property.UPNP.ARTIST.class);
        for (PersonWithRole artist : artists) {
            addProperty(new DIDLObject.Property.UPNP.ARTIST(artist));
        }
        return this;
    }

    public StorageMedium getStorageMedium() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.STORAGE_MEDIUM.class);
    }

    public MusicVideoClip setStorageMedium(StorageMedium storageMedium) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.STORAGE_MEDIUM(storageMedium));
        return this;
    }

    public String getAlbum() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM.class);
    }

    public MusicVideoClip setAlbum(String album) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM(album));
        return this;
    }

    public String getFirstScheduledStartTime() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.SCHEDULED_START_TIME.class);
    }

    public String[] getScheduledStartTimes() {
        List<String> list = getPropertyValues(DIDLObject.Property.UPNP.SCHEDULED_START_TIME.class);
        return list.toArray(new String[list.size()]);
    }

    public MusicVideoClip setScheduledStartTimes(String[] strings) {
        removeProperties(DIDLObject.Property.UPNP.SCHEDULED_START_TIME.class);
        for (String s : strings) {
            addProperty(new DIDLObject.Property.UPNP.SCHEDULED_START_TIME(s));
        }
        return this;
    }

    public String getFirstScheduledEndTime() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.SCHEDULED_END_TIME.class);
    }

    public String[] getScheduledEndTimes() {
        List<String> list = getPropertyValues(DIDLObject.Property.UPNP.SCHEDULED_END_TIME.class);
        return list.toArray(new String[list.size()]);
    }

    public MusicVideoClip setScheduledEndTimes(String[] strings) {
        removeProperties(DIDLObject.Property.UPNP.SCHEDULED_END_TIME.class);
        for (String s : strings) {
            addProperty(new DIDLObject.Property.UPNP.SCHEDULED_END_TIME(s));
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

    public MusicVideoClip setContributors(Person[] contributors) {
        removeProperties(DIDLObject.Property.DC.CONTRIBUTOR.class);
        for (Person p : contributors) {
            addProperty(new DIDLObject.Property.DC.CONTRIBUTOR(p));
        }
        return this;
    }

    public String getDate() {
        return getFirstPropertyValue(DIDLObject.Property.DC.DATE.class);
    }

    public MusicVideoClip setDate(String date) {
        replaceFirstProperty(new DIDLObject.Property.DC.DATE(date));
        return this;
    }


}

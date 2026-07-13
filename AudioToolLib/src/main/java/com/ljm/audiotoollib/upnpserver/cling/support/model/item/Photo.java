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
import com.ljm.audiotoollib.upnpserver.cling.support.model.Res;
import com.ljm.audiotoollib.upnpserver.cling.support.model.container.Container;

/**
 * @author Christian Bauer
 */
public class Photo extends ImageItem {

    public static final DIDLObject.Class CLASS = new DIDLObject.Class("object.item.imageItem.photo");

    public Photo() {
        setClazz(CLASS);
    }

    public Photo(Item other) {
        super(other);
    }

    public Photo(String id, Container parent, String title, String creator, String album, Res... resource) {
        this(id, parent.getId(), title, creator, album, resource);
    }

    public Photo(String id, String parentID, String title, String creator, String album, Res... resource) {
        super(id, parentID, title, creator, resource);
        setClazz(CLASS);
        if (album != null)
            setAlbum(album);
    }

    public String getAlbum() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM.class);
    }

    public Photo setAlbum(String album) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM(album));
        return this;
    }


}

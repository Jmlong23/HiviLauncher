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

package com.ljm.audiotoollib.upnpserver.cling.support.model.container;

import com.ljm.audiotoollib.upnpserver.cling.support.model.DIDLObject;
import com.ljm.audiotoollib.upnpserver.cling.support.model.StorageMedium;

/**
 * @author Christian Bauer
 */
public class StorageSystem extends Container {

    public static final DIDLObject.Class CLASS = new DIDLObject.Class("object.container.storageSystem");

    public StorageSystem() {
        setClazz(CLASS);
    }

    public StorageSystem(Container other) {
        super(other);
    }

    public StorageSystem(String id, Container parent, String title, String creator, Integer childCount,
                         Long storageTotal, Long storageUsed, Long storageFree, Long storageMaxPartition, StorageMedium storageMedium) {
        this(id, parent.getId(), title, creator, childCount, storageTotal, storageUsed, storageFree, storageMaxPartition, storageMedium);
    }

    public StorageSystem(String id, String parentID, String title, String creator, Integer childCount,
                         Long storageTotal, Long storageUsed, Long storageFree, Long storageMaxPartition, StorageMedium storageMedium) {
        super(id, parentID, title, creator, CLASS, childCount);
        if (storageTotal != null)
            setStorageTotal(storageTotal);
        if (storageUsed!= null)
            setStorageUsed(storageUsed);
        if (storageFree != null)
            setStorageFree(storageFree);
        if (storageMaxPartition != null)
            setStorageMaxPartition(storageMaxPartition);
        if (storageMedium != null)
            setStorageMedium(storageMedium);
    }

    public Long getStorageTotal() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.STORAGE_TOTAL.class);
    }

    public StorageSystem setStorageTotal(Long l) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.STORAGE_TOTAL(l));
        return this;
    }

    public Long getStorageUsed() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.STORAGE_USED.class);
    }

    public StorageSystem setStorageUsed(Long l) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.STORAGE_USED(l));
        return this;
    }

    public Long getStorageFree() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.STORAGE_FREE.class);
    }

    public StorageSystem setStorageFree(Long l) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.STORAGE_FREE(l));
        return this;
    }

    public Long getStorageMaxPartition() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.STORAGE_MAX_PARTITION.class);
    }

    public StorageSystem setStorageMaxPartition(Long l) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.STORAGE_MAX_PARTITION(l));
        return this;
    }

    public StorageMedium getStorageMedium() {
        return getFirstPropertyValue(DIDLObject.Property.UPNP.STORAGE_MEDIUM.class);
    }

    public StorageSystem setStorageMedium(StorageMedium storageMedium) {
        replaceFirstProperty(new DIDLObject.Property.UPNP.STORAGE_MEDIUM(storageMedium));
        return this;
    }

}

package com.ljm.audiotoollib.upnpserver.httpserver;

import android.content.Context;

import com.ljm.audiotoollib.upnpserver.cling.support.contentdirectory.ContentDirectoryException;
import com.ljm.audiotoollib.upnpserver.cling.support.model.BrowseResult;

public class ContentFactory {

    private static class Holder {
        private static final ContentFactory sInstance = new ContentFactory();
    }

    public static ContentFactory getInstance() {
        return Holder.sInstance;
    }

    private ContentFactory() {
    }

    private IContentFactory mContentFactory;

    public void setServerUrl(Context context, String url) {
        mContentFactory = new IContentFactory.ContentFactoryImpl(context, url);
    }

    public BrowseResult getContent(String objectID) throws ContentDirectoryException {
        if (mContentFactory != null) {
            return mContentFactory.getBrowseResult(objectID);
        }
        return null;
    }
}

package com.ljm.audiotoollib.upnpserver.httpserver;

import com.ljm.audiotoollib.upnpserver.cling.support.contentdirectory.AbstractContentDirectoryService;
import com.ljm.audiotoollib.upnpserver.cling.support.contentdirectory.ContentDirectoryException;
import com.ljm.audiotoollib.upnpserver.cling.support.model.BrowseFlag;
import com.ljm.audiotoollib.upnpserver.cling.support.model.BrowseResult;
import com.ljm.audiotoollib.upnpserver.cling.support.model.SortCriterion;

public class ContentDirectoryService extends AbstractContentDirectoryService {

    @Override
    public BrowseResult browse(String objectID, BrowseFlag browseFlag, String filter,
                               long firstResult, long maxResults, SortCriterion[] orderBy) throws ContentDirectoryException {
        return ContentFactory.getInstance().getContent(objectID);
    }

    @Override
    public BrowseResult search(String containerId, String searchCriteria, String filter,
                               long firstResult, long maxResults, SortCriterion[] orderBy) throws ContentDirectoryException {
        // You can override this method to implement searching!
        return super.search(containerId, searchCriteria, filter, firstResult, maxResults, orderBy);
    }
}

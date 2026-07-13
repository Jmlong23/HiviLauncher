package com.ljm.audiotoollib.upnpserver.httpserver;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;

import java.io.File;

class ContentResourceServlet extends DefaultServlet {

    @Override
    public Resource getResource(String pathInContext) {
        // String id = Utils.parseResourceId(pathInContext);
        // content://media/external/video/media/1611127029319529
        // Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id));
        try {
            File file = new File(pathInContext);
            if (file.exists()) return FileResource.newResource(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static class VideoResourceServlet extends ContentResourceServlet {
    }

    public static class AudioResourceServlet extends ContentResourceServlet {
    }
}
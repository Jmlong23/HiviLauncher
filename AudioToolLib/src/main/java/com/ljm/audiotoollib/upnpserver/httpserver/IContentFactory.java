package com.ljm.audiotoollib.upnpserver.httpserver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import com.ljm.audiotoollib.upnpserver.cling.support.contentdirectory.ContentDirectoryErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.contentdirectory.ContentDirectoryException;
import com.ljm.audiotoollib.upnpserver.cling.support.contentdirectory.DIDLParser;
import com.ljm.audiotoollib.upnpserver.cling.support.model.BrowseResult;
import com.ljm.audiotoollib.upnpserver.cling.support.model.DIDLContent;
import com.ljm.audiotoollib.upnpserver.cling.support.model.PersonWithRole;
import com.ljm.audiotoollib.upnpserver.cling.support.model.Res;
import com.ljm.audiotoollib.upnpserver.cling.support.model.container.Container;
import com.ljm.audiotoollib.upnpserver.cling.support.model.item.ImageItem;
import com.ljm.audiotoollib.upnpserver.cling.support.model.item.Item;
import com.ljm.audiotoollib.upnpserver.cling.support.model.item.Movie;
import com.ljm.audiotoollib.upnpserver.cling.support.model.item.MusicTrack;
import com.ljm.audiotoollib.upnpserver.core.ContentType;

import java.util.ArrayList;
import java.util.List;

interface IContentFactory {

    BrowseResult getBrowseResult(String objectID) throws ContentDirectoryException;

    // ------------------------------------------------------------
    // ---- Implement
    // ------------------------------------------------------------
    class ContentFactoryImpl implements IContentFactory {

        private final Context context;
        private final String baseUrl;

        public ContentFactoryImpl(Context context, String baseUrl) {
            this.context = context.getApplicationContext();
            this.baseUrl = baseUrl;
        }

        @Override
        public BrowseResult getBrowseResult(String objectID) throws ContentDirectoryException {
            Container container = getContent(objectID);
            DIDLContent didlContent = new DIDLContent();
            for (Container c : container.getContainers()) didlContent.addContainer(c);
            for (Item item : container.getItems()) didlContent.addItem(item);
            int count = container.getChildCount();
            String result;
            try {
                result = new DIDLParser().generate(didlContent, true);
            } catch (Exception e) {
                throw new ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, e.toString());
            }
            return new BrowseResult(result, count, count);
        }

        private Container getContent(String containerId) {
            if (ContentType.ALL.match(containerId)) {
                Container result = new Container();
                // 定义音频资源
                Container audioContainer = getAudioContents();
                audioContainer.setParentID(ContentType.ALL.id);
                audioContainer.setId(ContentType.AUDIO.id);
                audioContainer.setClazz(ContentType.AUDIO.clazz);
                audioContainer.setTitle(ContentType.AUDIO.name());
                result.addContainer(audioContainer);
                // 定义图片资源
                Container imageContainer = getImageContents();
                imageContainer.setParentID(ContentType.ALL.id);
                imageContainer.setId(ContentType.IMAGE.id);
                imageContainer.setClazz(ContentType.IMAGE.clazz);
                imageContainer.setTitle(ContentType.IMAGE.name());
                result.addContainer(imageContainer);
                // 定义视频资源
                Container videoContainer = getVideoContents();
                videoContainer.setParentID(ContentType.ALL.id);
                videoContainer.setId(ContentType.VIDEO.id);
                videoContainer.setClazz(ContentType.VIDEO.clazz);
                videoContainer.setTitle(ContentType.VIDEO.name());
                result.addContainer(videoContainer);
                // 3个子节点
                result.setChildCount(3);
                return result;
            } else if (ContentType.IMAGE.match(containerId)) {
                return getImageContents();
            } else if (ContentType.AUDIO.match(containerId)) {
                return getAudioContents();
            } else if (ContentType.VIDEO.match(containerId)) {
                return getVideoContents();
            } else {
                throw new IllegalArgumentException("can not parse containerId" + containerId);
            }
        }

        private Container getVideoContents() {
            IMediaContentDao contentDao = new IMediaContentDao.MediaContentDao(baseUrl);
            List<Item> items = contentDao.getVideoItems(context);
            return getContents(items);
        }

        private Container getAudioContents() {
            IMediaContentDao contentDao = new IMediaContentDao.MediaContentDao(baseUrl);
            List<Item> items = contentDao.getAudioItems(context);
            return getContents(items);
        }

        private Container getImageContents() {
            IMediaContentDao contentDao = new IMediaContentDao.MediaContentDao(baseUrl);
            List<Item> items = contentDao.getImageItems(context);
            return getContents(items);
        }

        private Container getContents(List<Item> items) {
            Container result = new Container();
            for (Item item : items) result.addItem(item);
            result.setChildCount(items.size());
            return result;
        }

    }

    // ------------------------------------------------------------
    // ---- MediaContent
    // ------------------------------------------------------------
    interface IMediaContentDao {

        List<Item> getImageItems( Context context);


        List<Item> getAudioItems(Context context);


        List<Item> getVideoItems(Context context);

        // -------------------------------------------------------
        // ---- Implement
        // -------------------------------------------------------
        final class MediaContentDao implements IMediaContentDao {

            private static final String[] CONTENT_IMAGE_COLUMNS = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE,
                    MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE};
            private static final String[] CONTENT_AUDIO_COLUMNS = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.ARTIST,
                    MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.ALBUM};
            private static final String[] CONTENT_VIDEO_COLUMNS = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.ARTIST,
                    MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RESOLUTION};

            private final String mBaseUrl;

            public MediaContentDao(String baseUrl) {
                mBaseUrl = baseUrl;
            }

            @Override
            public List<Item> getImageItems(Context context) {
                List<Item> items = new ArrayList<>();
                try (Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, CONTENT_IMAGE_COLUMNS, null, null, null)) {
                    if (cursor == null) return items;
                    cursor.moveToFirst();
                    while (cursor.moveToNext()) {
                        @SuppressLint("Range") String id = String.valueOf(cursor.getInt(cursor.getColumnIndex(CONTENT_IMAGE_COLUMNS[0])));
                        @SuppressLint("Range") String title = cursor.getString(cursor.getColumnIndex(CONTENT_IMAGE_COLUMNS[1]));
                        @SuppressLint("Range") String data = cursor.getString(cursor.getColumnIndex(CONTENT_IMAGE_COLUMNS[2]));
                        @SuppressLint("Range") String mimeType = cursor.getString(cursor.getColumnIndex(CONTENT_IMAGE_COLUMNS[3]));
                        @SuppressLint("Range") long size = cursor.getLong(cursor.getColumnIndex(CONTENT_IMAGE_COLUMNS[4]));
                        String url = mBaseUrl + data;
                        Res res = new Res(mimeType, size, "", null, url);
                        ImageItem imageItem = new ImageItem(id, ContentType.IMAGE.id, title, "", res);
                        items.add(imageItem);
                    }
                }
                return items;
            }

            public List<Item> getAudioItems(Context context) {
                List<Item> items = new ArrayList<>();
                try (Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, CONTENT_AUDIO_COLUMNS, null, null, null)) {
                    if (cursor == null) return items;
                    cursor.moveToFirst();
                    while (cursor.moveToNext()) {
                        @SuppressLint("Range") String id = String.valueOf(cursor.getInt(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[0])));
                        @SuppressLint("Range") String title = cursor.getString(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[1]));
                        @SuppressLint("Range") String creator = cursor.getString(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[2]));
                        @SuppressLint("Range") String data = cursor.getString(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[3]));
                        @SuppressLint("Range") String mimeType = cursor.getString(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[4]));
                        @SuppressLint("Range") long size = cursor.getLong(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[5]));
                        // long duration = cursor.getLong(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[6]));
                        // String durationStr = ModelUtil.toTimeString(duration / 1000);
                        @SuppressLint("Range") String album = cursor.getString(cursor.getColumnIndex(CONTENT_AUDIO_COLUMNS[6]));
                        String url = mBaseUrl + data;
                        Res res = new Res(mimeType, size, "", null, url);
                        MusicTrack musicTrack = new MusicTrack(id, ContentType.AUDIO.id, title, creator, album, new PersonWithRole(creator), res);
                        items.add(musicTrack);
                    }
                }

                return items;
            }


            public List<Item> getVideoItems(Context context) {
                List<Item> items = new ArrayList<>();
                try (Cursor cur = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, CONTENT_VIDEO_COLUMNS, null, null, null)) {
                    if (cur == null) return items;
                    cur.moveToFirst();
                    while (cur.moveToNext()) {
                        @SuppressLint("Range") String id = String.valueOf(cur.getInt(cur.getColumnIndex(CONTENT_VIDEO_COLUMNS[0])));
                        @SuppressLint("Range") String title = cur.getString(cur.getColumnIndex(CONTENT_VIDEO_COLUMNS[1]));
                        @SuppressLint("Range") String creator = cur.getString(cur.getColumnIndex(CONTENT_VIDEO_COLUMNS[2]));
                        String data = cur.getString(cur.getColumnIndexOrThrow(CONTENT_VIDEO_COLUMNS[3]));
                        @SuppressLint("Range") String mimeType = cur.getString(cur.getColumnIndex(CONTENT_VIDEO_COLUMNS[4]));
                        @SuppressLint("Range") long size = cur.getLong(cur.getColumnIndex(CONTENT_VIDEO_COLUMNS[5]));
                        // long duration = cur.getLong(cur.getColumnIndex(CONTENT_VIDEO_COLUMNS[6]));
                        // String durationStr = ModelUtil.toTimeString(duration / 1000);
                        @SuppressLint("Range") String resolution = cur.getString(cur.getColumnIndex(CONTENT_VIDEO_COLUMNS[6]));
                        String url = mBaseUrl + data;
                        Res res = new Res(mimeType, size, "", null, url);
                        res.setResolution(resolution);
                        Movie movie = new Movie(id, ContentType.VIDEO.id, title, creator, res);
                        items.add(movie);
                    }
                }
                return items;
            }
        }
    }
}

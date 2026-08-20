package com.hivi.launcher.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

public final class UpdatePackageProvider extends ContentProvider {
    private static final String AUTHORITY_SUFFIX = ".update.fileprovider";
    private static final String PATH_PACKAGE = "package";
    private static final String UPDATE_DIRECTORY_NAME = "updates";
    private static final String UPDATE_PACKAGE_NAME = "hivi-launcher-update.apk";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    public static Uri getPackageUri(Context context) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(PATH_PACKAGE)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        assertPackageUri(uri);
        return APK_MIME_TYPE;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        assertPackageUri(uri);
        File packageFile = getPackageFile();
        String[] columns = projection == null ? new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        } : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(UPDATE_PACKAGE_NAME);
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(packageFile.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        assertPackageUri(uri);
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Update package is read-only.");
        }
        File packageFile = getPackageFile();
        if (!packageFile.isFile()) {
            throw new FileNotFoundException("Update package is unavailable.");
        }
        return ParcelFileDescriptor.open(packageFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Update package is read-only.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update package is read-only.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update package is read-only.");
    }

    private File getPackageFile() {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Update package provider is unavailable.");
        }
        File baseDirectory = context.getExternalFilesDir(null);
        if (baseDirectory == null) {
            baseDirectory = context.getCacheDir();
        }
        return new File(new File(baseDirectory, UPDATE_DIRECTORY_NAME), UPDATE_PACKAGE_NAME);
    }

    private static void assertPackageUri(Uri uri) {
        if (uri == null || uri.getPathSegments().size() != 1
                || !PATH_PACKAGE.equals(uri.getLastPathSegment())) {
            throw new IllegalArgumentException("Unsupported update package URI.");
        }
    }
}

package com.seiryu.sunoexport;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.OutputStream;

public final class Storage {
    private Storage() {}

    public static Uri writeDownload(Context context, String relativeDir, String displayName, String mime, byte[] data) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Suno Export Toolkit/" + relativeDir);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Cannot create output document.");
        boolean ok = false;
        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("Cannot open output stream.");
            out.write(data);
            ok = true;
        } finally {
            ContentValues finish = new ContentValues();
            finish.put(MediaStore.Downloads.IS_PENDING, ok ? 0 : 1);
            resolver.update(uri, finish, null, null);
            if (!ok) resolver.delete(uri, null, null);
        }
        return uri;
    }
}

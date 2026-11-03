package com.canju12.zhuzicanju;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Application-wide image cache for shared chess and UI assets. */
public final class UiAssetCache {
    private static final Object LOCK = new Object();
    private static final Map<String, ExternalEntry> EXTERNAL = new HashMap<>();
    private static final Map<Integer, Bitmap> RESOURCES = new HashMap<>();

    private UiAssetCache() {
    }

    public static Bitmap getBitmap(Context context, String externalFileName, int resourceId) {
        if (context == null) return null;
        File file = externalFile(externalFileName);
        synchronized (LOCK) {
            if (file.isFile()) {
                ExternalEntry entry = EXTERNAL.get(externalFileName);
                long length = file.length();
                long modified = file.lastModified();
                if (entry != null && entry.length == length && entry.lastModified == modified
                        && isUsable(entry.bitmap)) {
                    return entry.bitmap;
                }
                Bitmap bitmap = decodeFile(file);
                if (bitmap != null) {
                    EXTERNAL.put(externalFileName, new ExternalEntry(length, modified, bitmap));
                    return bitmap;
                }
            }

            Bitmap cached = RESOURCES.get(resourceId);
            if (isUsable(cached)) return cached;
            Bitmap bitmap = decodeResource(context, resourceId);
            if (bitmap != null) RESOURCES.put(resourceId, bitmap);
            return bitmap;
        }
    }

    public static Bitmap getExternal(Context context, String externalFileName) {
        return getBitmap(context, externalFileName, 0);
    }

    public static Bitmap getRoom(Context context) {
        return getBitmap(context, "room.png", R.drawable.room);
    }

    public static Bitmap getBoard(Context context) {
        return getBitmap(context, "board.png", R.drawable.board);
    }

    public static void clear() {
        synchronized (LOCK) {
            EXTERNAL.clear();
            RESOURCES.clear();
        }
    }

    private static File externalFile(String fileName) {
        File root = new File(new File(Environment.getExternalStorageDirectory(), "canju12"), "uistyle");
        return new File(root, fileName);
    }

    private static Bitmap decodeFile(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap decodeResource(Context context, int resourceId) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeResource(context.getResources(), resourceId, options);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isUsable(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled();
    }

    private static final class ExternalEntry {
        final long length;
        final long lastModified;
        final Bitmap bitmap;

        ExternalEntry(long length, long lastModified, Bitmap bitmap) {
            this.length = length;
            this.lastModified = lastModified;
            this.bitmap = bitmap;
        }
    }
}

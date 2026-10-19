package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The I/O half of the rotating wallpaper: lists the pictures in the wallpaper folder and keeps the
 * WallpaperRotation state in shared preferences, for the home screen and the Wallpaper pop-up alike.
 */
final class WallpaperFolder {
    private static final String TAG = "TvHome.Wallpaper";
    private static final String PREFS = "wallpaper";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_SHOWN_AT = "shown_at";
    private static final String KEY_NEWEST_SEEN = "newest_seen";
    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};

    private WallpaperFolder() {
    }

    /** The pictures in the folder, sorted by name; null when the folder is not set or cannot be read. */
    static List<WallpaperRotation.Picture> list(File folder) {
        if (folder == null) return null;
        File[] files = folder.listFiles(f -> f.isFile() && f.length() > 0 && isPicture(f.getName()));
        if (files == null) {
            Log.w(TAG, "cannot read " + folder + " (missing folder or READ_EXTERNAL_STORAGE)");
            return null;
        }
        List<WallpaperRotation.Picture> pictures = new ArrayList<>();
        for (File f : files) pictures.add(new WallpaperRotation.Picture(f.getName(), f.lastModified()));
        Collections.sort(pictures, Comparator.comparing(p -> p.name));
        return pictures;
    }

    static WallpaperRotation.State load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new WallpaperRotation.State(prefs.getString(KEY_CURRENT, null),
                prefs.getLong(KEY_SHOWN_AT, 0), prefs.getLong(KEY_NEWEST_SEEN, 0));
    }

    static void save(Context ctx, WallpaperRotation.State state) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CURRENT, state.current)
                .putLong(KEY_SHOWN_AT, state.shownAtMs)
                .putLong(KEY_NEWEST_SEEN, state.newestSeenMs)
                .apply();
    }

    private static boolean isPicture(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}

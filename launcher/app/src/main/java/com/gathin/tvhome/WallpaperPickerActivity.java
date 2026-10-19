package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Wallpaper pop-up, opened from the Wallpaper tile: thumbnails of the pictures in the wallpaper folder.
 * OK on one makes it the wallpaper (WallpaperRotation.select) and closes the pop-up; the home screen shows
 * it on resume. Back closes without a change.
 */
public class WallpaperPickerActivity extends Activity {
    private static final String TAG = "TvHome.Wallpaper";
    private static final int COLUMNS = 4;
    /** Thumbnails are decoded at about this width, so twelve of them cost little memory. */
    private static final int THUMB_WIDTH_PX = 400;

    private final Handler main = new Handler(Looper.getMainLooper());
    /** One thread decodes the thumbnails, off the main thread so D-pad moves stay smooth. */
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private File folder;
    private List<WallpaperRotation.Picture> pictures = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpaper_picker);
        Config cfg = Config.load(this);
        folder = cfg.wallpaperFolder;
        List<WallpaperRotation.Picture> listed = WallpaperFolder.list(folder);
        if (listed != null) pictures = listed;

        TextView hint = findViewById(R.id.wallpaperHint);
        String path = folder != null ? folder.getPath() : "";
        if (pictures.isEmpty()) {
            hint.setText(getString(R.string.wallpaper_empty, path));
        } else if (cfg.wallpaperRotateMinutes > 0) {
            hint.setText(getString(R.string.wallpaper_hint, cfg.wallpaperRotateMinutes, path));
        } else {
            hint.setText(getString(R.string.wallpaper_hint_fixed, path));
        }

        String current = WallpaperFolder.load(this).current;
        RecyclerView grid = findViewById(R.id.wallpaperGrid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setAdapter(new ThumbAdapter(current));
        int start = Math.max(0, indexOf(current));
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(start);
            if (h != null) h.itemView.requestFocus();
        });
    }

    @Override
    protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    private int indexOf(String name) {
        for (int i = 0; i < pictures.size(); i++) {
            if (pictures.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    private void pick(String name) {
        WallpaperRotation.State state = WallpaperRotation.select(
                pictures, WallpaperFolder.load(this), System.currentTimeMillis(), name);
        WallpaperFolder.save(this, state);
        Log.i(TAG, "picked " + name);
        finish();
    }

    /** Decodes a picture at thumbnail size; null when it cannot be read. Runs on the decoder thread. */
    private static Bitmap decodeThumb(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), opts);
        int sample = 1;
        while (opts.outWidth / (sample * 2) >= THUMB_WIDTH_PX) sample *= 2;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "no memory for thumbnail " + file, e);
            return null;
        }
    }

    private final class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.Holder> {
        private final String current;

        ThumbAdapter(String current) {
            this.current = current;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final View badge;

            Holder(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                badge = v.findViewById(R.id.current);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String name = pictures.get(position).name;
            h.badge.setVisibility(name.equals(current) ? View.VISIBLE : View.GONE);
            h.thumb.setImageDrawable(null);
            h.thumb.setTag(name);
            h.itemView.setOnClickListener(v -> pick(name));
            File file = new File(folder, name);
            decoder.execute(() -> {
                Bitmap bmp = decodeThumb(file);
                // The view may have been rebound to another picture while this one decoded.
                main.post(() -> {
                    if (bmp != null && name.equals(h.thumb.getTag())) h.thumb.setImageBitmap(bmp);
                });
            });
        }

        @Override
        public int getItemCount() {
            return pictures.size();
        }
    }
}

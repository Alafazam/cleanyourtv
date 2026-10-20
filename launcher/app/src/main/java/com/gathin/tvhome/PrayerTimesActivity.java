package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prayer Times, opened from its tile: today's six times for the configured place, the next one highlighted with
 * a countdown, over the current wallpaper. Refreshes every minute; Back closes it.
 */
public class PrayerTimesActivity extends Activity {
    private static final String TAG = "TvHome.Prayer";
    private static final long MINUTE_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private Config cfg;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = Config.load(this);
        if (cfg.prayerPlace == null) {
            Log.w(TAG, "prayer times are off or the place in config.xml is invalid");
            finish();
            return;
        }
        setContentView(R.layout.activity_prayer_times);
        loadBackdrop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.postDelayed(tick, MINUTE_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    private void refresh() {
        PrayerUi.Day day = PrayerUi.today(cfg);
        if (day != null) PrayerUi.bindPanel(findViewById(R.id.prayerPanel), day);
    }

    /** The current wallpaper from the folder, else the theme's wallpaper.jpg, else plain black. */
    private void loadBackdrop() {
        String current = WallpaperFolder.load(this).current;
        File file = current != null && cfg.wallpaperFolder != null ? new File(cfg.wallpaperFolder, current) : cfg.wallpaper;
        if (file == null || !file.isFile()) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        ImageView backdrop = findViewById(R.id.prayerBackdrop);
        decoder.execute(() -> {
            Bitmap bmp = WallpaperFolder.decode(file, dm.widthPixels, dm.heightPixels);
            if (bmp != null) handler.post(() -> {
                if (isDestroyed()) bmp.recycle();
                else backdrop.setImageBitmap(bmp);
            });
        });
    }
}

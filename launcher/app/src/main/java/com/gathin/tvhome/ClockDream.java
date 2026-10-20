package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow.
 * Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * With dream_photos on and pictures in the wallpaper folder, those pictures fill the screen behind the
 * clock instead of the glow, one at a time, starting with the one after the current wallpaper. Pictures
 * decode off the main thread at screen size, and only one is held at a time. No network.
 */
public class ClockDream extends DreamService {

    private static final String TAG = "TvHome.ClockDream";
    private static final long MOVE_MS = 60_000L;
    private static final long PHOTO_FADE_OUT_MS = 700L;
    private static final long PHOTO_FADE_IN_MS = 1500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;

    /** Photo mode: set in onAttachedToWindow when the folder has pictures, else everything below stays null. */
    private ImageView photo;
    private File folder;
    private List<WallpaperRotation.Picture> pictures;
    private String shownPhoto;
    private int photoMinutes;
    private int minutesOnPhoto;
    private ExecutorService decoder;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            if (photo != null) {
                minutesOnPhoto++;
                if (minutesOnPhoto >= photoMinutes) showNextPhoto();
            } else {
                shiftGlow();
            }
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);

        Config cfg = Config.load(this);
        List<WallpaperRotation.Picture> listed = cfg.dreamPhotos ? WallpaperFolder.list(cfg.wallpaperFolder) : null;
        if (listed != null && !listed.isEmpty()) {
            folder = cfg.wallpaperFolder;
            pictures = listed;
            photoMinutes = cfg.dreamPhotoMinutes;
            shownPhoto = WallpaperFolder.load(this).current;   // start after the current wallpaper
            decoder = Executors.newSingleThreadExecutor();
            photo = findViewById(R.id.dreamPhoto);
            photo.setVisibility(View.VISIBLE);
            findViewById(R.id.dreamVeil).setVisibility(View.VISIBLE);
            ambient.setVisibility(View.GONE);
            Log.i(TAG, "photo mode: " + pictures.size() + " pictures, " + photoMinutes + " min each");
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        if (photo != null) showNextPhoto();
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (decoder != null) decoder.shutdownNow();
        if (photo != null) releasePhoto(photo.getDrawable());
        super.onDetachedFromWindow();
    }

    /** Decodes the next picture by name on the decoder thread, then fades it in over the current one. */
    private void showNextPhoto() {
        minutesOnPhoto = 0;
        String name = WallpaperRotation.next(pictures, shownPhoto);
        if (name == null) return;
        shownPhoto = name;
        File file = new File(folder, name);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        decoder.execute(() -> {
            Bitmap bmp = WallpaperFolder.decode(file, dm.widthPixels, dm.heightPixels);
            if (bmp != null) handler.post(() -> fadeTo(bmp));
        });
    }

    private void fadeTo(Bitmap bmp) {
        if (photo == null || decoder.isShutdown()) {
            bmp.recycle();
            return;
        }
        photo.animate().alpha(0f).setDuration(photo.getDrawable() == null ? 0 : PHOTO_FADE_OUT_MS).withEndAction(() -> {
            Drawable old = photo.getDrawable();
            photo.setImageBitmap(bmp);
            releasePhoto(old);
            photo.animate().alpha(1f).setDuration(PHOTO_FADE_IN_MS).start();
        }).start();
    }

    private static void releasePhoto(Drawable d) {
        if (d instanceof BitmapDrawable) ((BitmapDrawable) d).getBitmap().recycle();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}

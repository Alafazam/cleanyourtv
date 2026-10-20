package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** The rules that pick the wallpaper from the folder: newest first, then rotation by name. */
public class WallpaperRotationTest {

    private static final long MINUTE = 60_000L;
    private static final long INTERVAL = 30 * MINUTE;
    private static final long T0 = 1_000_000_000L;

    private static WallpaperRotation.Picture pic(String name, long modified) {
        return new WallpaperRotation.Picture(name, modified);
    }

    /** Three pictures, all older than T0; "c" is the newest. */
    private static final List<WallpaperRotation.Picture> THREE = Arrays.asList(
            pic("b.jpg", 200), pic("a.jpg", 100), pic("c.jpg", 300));

    @Test
    public void anEmptyFolderShowsNothing() {
        WallpaperRotation.State s = WallpaperRotation.choose(Collections.emptyList(), WallpaperRotation.State.EMPTY, T0, INTERVAL);
        assertNull(s.current);
    }

    @Test
    public void theFirstCheckShowsTheNewestPicture() {
        WallpaperRotation.State s = WallpaperRotation.choose(THREE, WallpaperRotation.State.EMPTY, T0, INTERVAL);
        assertEquals("c.jpg", s.current);
        assertEquals(T0, s.shownAtMs);
        assertEquals(300, s.newestSeenMs);
    }

    @Test
    public void thePictureStaysUntilTheIntervalHasPassed() {
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 300);
        WallpaperRotation.State s = WallpaperRotation.choose(THREE, shown, T0 + INTERVAL - 1, INTERVAL);
        assertEquals("a.jpg", s.current);
        assertEquals(T0, s.shownAtMs);
    }

    @Test
    public void afterTheIntervalTheNextPictureByNameShows() {
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 300);
        WallpaperRotation.State s = WallpaperRotation.choose(THREE, shown, T0 + INTERVAL, INTERVAL);
        assertEquals("b.jpg", s.current);
        assertEquals(T0 + INTERVAL, s.shownAtMs);
    }

    @Test
    public void rotationWrapsFromTheLastPictureToTheFirst() {
        WallpaperRotation.State shown = new WallpaperRotation.State("c.jpg", T0, 300);
        WallpaperRotation.State s = WallpaperRotation.choose(THREE, shown, T0 + INTERVAL, INTERVAL);
        assertEquals("a.jpg", s.current);
    }

    @Test
    public void aZeroIntervalNeverRotates() {
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 300);
        WallpaperRotation.State s = WallpaperRotation.choose(THREE, shown, T0 + 365 * 24 * 60 * MINUTE, 0);
        assertEquals("a.jpg", s.current);
    }

    @Test
    public void aNewPictureFromThePhoneShowsAtOnce() {
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 300);
        List<WallpaperRotation.Picture> withNew = Arrays.asList(
                pic("a.jpg", 100), pic("b.jpg", 200), pic("c.jpg", 300), pic("phone.jpg", T0 + MINUTE));
        WallpaperRotation.State s = WallpaperRotation.choose(withNew, shown, T0 + 2 * MINUTE, INTERVAL);
        assertEquals("phone.jpg", s.current);
        assertEquals(T0 + MINUTE, s.newestSeenMs);
    }

    @Test
    public void aNewPictureIsShownOnlyOnceThenRotationGoesOn() {
        WallpaperRotation.State shown = new WallpaperRotation.State("phone.jpg", T0, T0);
        List<WallpaperRotation.Picture> withNew = Arrays.asList(pic("a.jpg", 100), pic("phone.jpg", T0));
        WallpaperRotation.State kept = WallpaperRotation.choose(withNew, shown, T0 + MINUTE, INTERVAL);
        assertEquals("phone.jpg", kept.current);
        WallpaperRotation.State next = WallpaperRotation.choose(withNew, kept, T0 + INTERVAL, INTERVAL);
        assertEquals("a.jpg", next.current);
    }

    @Test
    public void aDeletedPictureIsReplacedByTheFirstByName() {
        WallpaperRotation.State shown = new WallpaperRotation.State("gone.jpg", T0, 300);
        WallpaperRotation.State s = WallpaperRotation.choose(THREE, shown, T0 + MINUTE, INTERVAL);
        assertEquals("a.jpg", s.current);
        assertEquals(T0 + MINUTE, s.shownAtMs);
    }

    @Test
    public void aClockThatGoesBackRestartsTheInterval() {
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 300);
        WallpaperRotation.State s = WallpaperRotation.choose(THREE, shown, T0 - 10 * MINUTE, INTERVAL);
        assertEquals("a.jpg", s.current);
        assertEquals(T0 - 10 * MINUTE, s.shownAtMs);
    }

    @Test
    public void choosingTwiceGivesTheSameResult() {
        WallpaperRotation.State first = WallpaperRotation.choose(THREE, WallpaperRotation.State.EMPTY, T0, INTERVAL);
        WallpaperRotation.State second = WallpaperRotation.choose(THREE, first, T0, INTERVAL);
        assertEquals(first.current, second.current);
        assertEquals(first.shownAtMs, second.shownAtMs);
        assertEquals(first.newestSeenMs, second.newestSeenMs);
    }

    @Test
    public void nextGivesThePictureAfterByName() {
        assertEquals("b.jpg", WallpaperRotation.next(THREE, "a.jpg"));
        assertEquals("c.jpg", WallpaperRotation.next(THREE, "b.jpg"));
    }

    @Test
    public void nextWrapsFromTheLastPictureToTheFirst() {
        assertEquals("a.jpg", WallpaperRotation.next(THREE, "c.jpg"));
    }

    @Test
    public void nextStartsAtTheFirstWhenNothingOrAMissingPictureIsGiven() {
        assertEquals("a.jpg", WallpaperRotation.next(THREE, null));
        assertEquals("a.jpg", WallpaperRotation.next(THREE, "gone.jpg"));
    }

    @Test
    public void nextOnAnEmptyFolderIsNull() {
        assertNull(WallpaperRotation.next(Collections.emptyList(), "a.jpg"));
    }

    @Test
    public void selectShowsThePickedPictureAndRestartsTheInterval() {
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 300);
        WallpaperRotation.State s = WallpaperRotation.select(THREE, shown, T0 + 5 * MINUTE, "b.jpg");
        assertEquals("b.jpg", s.current);
        assertEquals(T0 + 5 * MINUTE, s.shownAtMs);
        // The pick is not replaced by the newest picture on the next check.
        WallpaperRotation.State after = WallpaperRotation.choose(THREE, s, T0 + 6 * MINUTE, INTERVAL);
        assertEquals("b.jpg", after.current);
    }

    @Test
    public void selectMarksEveryPictureSeen() {
        List<WallpaperRotation.Picture> withNew = Arrays.asList(pic("a.jpg", 100), pic("phone.jpg", T0));
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 100);
        WallpaperRotation.State s = WallpaperRotation.select(withNew, shown, T0 + MINUTE, "a.jpg");
        assertEquals(T0, s.newestSeenMs);
        assertEquals("a.jpg", WallpaperRotation.choose(withNew, s, T0 + 2 * MINUTE, INTERVAL).current);
    }

    @Test
    public void selectingAMissingPictureChangesNothing() {
        WallpaperRotation.State shown = new WallpaperRotation.State("a.jpg", T0, 300);
        assertSame(shown, WallpaperRotation.select(THREE, shown, T0 + MINUTE, "gone.jpg"));
    }
}

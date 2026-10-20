package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the wallpaper from a folder of pictures. Pure logic, no Android and no I/O, so it can be
 * tested on the JVM; MainActivity lists the folder, keeps the state and draws the picture.
 *
 * Rules, in this order:
 * 1. A picture newer than any seen before (sent from a phone, say) is shown at once.
 * 2. When the current picture is gone, the first one (by name) is shown.
 * 3. When the interval has passed since the last change, the next one by name is shown, wrapping round.
 *    An interval of 0 or less never rotates.
 * 4. Otherwise the current picture stays.
 */
final class WallpaperRotation {

    /** One picture in the folder: its file name and its last-modified time. */
    static final class Picture {
        final String name;
        final long modifiedMs;

        Picture(String name, long modifiedMs) {
            this.name = name;
            this.modifiedMs = modifiedMs;
        }
    }

    /** What the launcher remembers between checks. {@link #EMPTY} before the first check. */
    static final class State {
        static final State EMPTY = new State(null, 0, 0);

        /** File name of the picture on screen, or null. */
        final String current;
        /** When that picture went on screen. */
        final long shownAtMs;
        /** The newest modified time seen in the folder so far. */
        final long newestSeenMs;

        State(String current, long shownAtMs, long newestSeenMs) {
            this.current = current;
            this.shownAtMs = shownAtMs;
            this.newestSeenMs = newestSeenMs;
        }
    }

    private WallpaperRotation() {
    }

    /** The state after a check: its {@code current} is the picture to show, null when the folder is empty. */
    static State choose(List<Picture> pictures, State state, long nowMs, long intervalMs) {
        if (pictures.isEmpty()) return new State(null, state.shownAtMs, state.newestSeenMs);

        List<Picture> byName = new ArrayList<>(pictures);
        Collections.sort(byName, Comparator.comparing(p -> p.name));
        Picture newest = Collections.max(byName, Comparator.comparingLong(p -> p.modifiedMs));
        long newestSeen = Math.max(state.newestSeenMs, newest.modifiedMs);

        if (newest.modifiedMs > state.newestSeenMs) {
            return new State(newest.name, nowMs, newestSeen);
        }
        int index = indexOf(byName, state.current);
        if (index < 0) {
            return new State(byName.get(0).name, nowMs, newestSeen);
        }
        long elapsed = nowMs - state.shownAtMs;
        if (elapsed < 0) {
            // The clock went back: start the interval again from now.
            return new State(state.current, nowMs, newestSeen);
        }
        if (intervalMs > 0 && elapsed >= intervalMs) {
            return new State(next(byName, state.current), nowMs, newestSeen);
        }
        return new State(state.current, state.shownAtMs, newestSeen);
    }

    /**
     * The state after a picture is picked in the Wallpaper pop-up: that picture, with the interval starting
     * again from now. Every picture counts as seen, so the pick is not replaced by "newest" on the next check.
     * A name that is not in the folder leaves the state as it was.
     */
    static State select(List<Picture> pictures, State state, long nowMs, String name) {
        if (indexOf(pictures, name) < 0) return state;
        long newestSeen = state.newestSeenMs;
        for (Picture p : pictures) newestSeen = Math.max(newestSeen, p.modifiedMs);
        return new State(name, nowMs, newestSeen);
    }

    /**
     * The picture after {@code after} by name, wrapping round; the first one when {@code after} is null or
     * gone; null when there are no pictures. The screensaver walks the folder with it.
     */
    static String next(List<Picture> pictures, String after) {
        if (pictures.isEmpty()) return null;
        List<Picture> byName = new ArrayList<>(pictures);
        Collections.sort(byName, Comparator.comparing(p -> p.name));
        int index = indexOf(byName, after);
        return byName.get(index < 0 ? 0 : (index + 1) % byName.size()).name;
    }

    private static int indexOf(List<Picture> pictures, String name) {
        if (name == null) return -1;
        for (int i = 0; i < pictures.size(); i++) {
            if (pictures.get(i).name.equals(name)) return i;
        }
        return -1;
    }
}

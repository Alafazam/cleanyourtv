package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Prayer times against published timetables, and the current/next rules. */
public class PrayerTimesTest {

    /** Bengaluru, India time, Karachi method (18/18), Hanafi Asr: the settings this TV uses. */
    private static final PrayerTimes.Settings BENGALURU =
            new PrayerTimes.Settings(12.9716, 77.5946, 5.5, 18, 18, 2);
    /** Published times may round differently by a minute. */
    private static final int TOLERANCE_MIN = 1;

    /** The English strings from values/strings.xml. */
    private static final PrayerTimes.Words ENGLISH = new PrayerTimes.Words() {
        @Override public String now(String name, String time) { return "Now " + name + " " + time; }
        @Override public String next(String name, String time, String countdown) { return "Next " + name + " " + time + ", " + countdown; }
        @Override public String inMinutes(int m) { return "in " + m + " min"; }
        @Override public String inHours(int h) { return "in " + h + " h"; }
        @Override public String inHoursMinutes(int h, int m) { return "in " + h + " h " + m + " min"; }
        @Override public String angles(int f, int i) { return f == i ? "Fajr and Isha " + f + "°" : "Fajr " + f + "°, Isha " + i + "°"; }
        @Override public String asr(boolean hanafi) { return hanafi ? "Hanafi Asr" : "Shafi'i Asr"; }
    };

    private static int hm(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** Expected Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha from api.aladhan.com (method 1, school 1). */
    private static void assertDay(LocalDate date, String... expected) {
        int[] got = PrayerTimes.compute(date, BENGALURU);
        for (int i = 0; i < PrayerTimes.COUNT; i++) {
            int diff = Math.abs(got[i] - hm(expected[i]));
            assertTrue(date + " " + PrayerTimes.NAMES[i] + ": expected " + expected[i] + ", got "
                    + PrayerTimes.format(got[i], true, Locale.ENGLISH), diff <= TOLERANCE_MIN);
        }
    }

    @Test
    public void matchesTheTimetableAcrossTheYear() {
        assertDay(LocalDate.of(2026, 1, 1), "05:26", "06:42", "12:23", "16:28", "18:05", "19:20");
        assertDay(LocalDate.of(2026, 3, 21), "05:13", "06:23", "12:27", "16:47", "18:31", "19:41");
        assertDay(LocalDate.of(2026, 6, 21), "04:36", "05:55", "12:21", "16:56", "18:48", "20:07");
        assertDay(LocalDate.of(2026, 10, 20), "04:59", "06:11", "12:04", "16:21", "17:58", "19:09");
        assertDay(LocalDate.of(2026, 12, 21), "05:21", "06:37", "12:18", "16:22", "17:59", "19:15");
    }

    @Test
    public void placeFromConfigGivesIndiaTimeAndTheSameTimes() {
        PrayerTimes.Place p = PrayerTimes.Place.parse("Bengaluru", "12.9716", "77.5946", "Asia/Kolkata", 18, 18, 2);
        LocalDate d = LocalDate.of(2026, 10, 20);
        assertEquals(5.5, p.settingsFor(d).utcOffsetHours, 0.0001);
        assertEquals(PrayerTimes.compute(d, BENGALURU)[PrayerTimes.DHUHR], PrayerTimes.compute(d, p.settingsFor(d))[PrayerTimes.DHUHR]);
        assertEquals("Fajr and Isha 18° · Hanafi Asr", p.methodLabel(ENGLISH));
    }

    @Test
    public void anEmptyTimeZoneMeansTheTvsOwn() {
        PrayerTimes.Place p = PrayerTimes.Place.parse("X", "12.9716", "77.5946", "", 18, 18, 2);
        assertEquals(java.time.ZoneId.systemDefault(), p.zone);
    }

    @Test
    public void invalidPlaceValuesTurnPrayerTimesOff() {
        assertNull(PrayerTimes.Place.parse("X", "", "", "", 18, 18, 1));
        assertNull(PrayerTimes.Place.parse("X", "abc", "77", "Asia/Kolkata", 18, 18, 2));
        assertNull(PrayerTimes.Place.parse("X", "12.9", "77.5", "Not/AZone", 18, 18, 2));
        assertNull(PrayerTimes.Place.parse("X", "60", "10", "Europe/Oslo", 18, 18, 2));
        assertNull(PrayerTimes.Place.parse("X", "12.9", "77.5", "Asia/Kolkata", 18, 18, 3));
    }

    @Test
    public void hanafiAsrIsLaterThanShafii() {
        LocalDate d = LocalDate.of(2026, 10, 20);
        PrayerTimes.Settings shafii = new PrayerTimes.Settings(12.9716, 77.5946, 5.5, 18, 18, 1);
        assertTrue(PrayerTimes.compute(d, BENGALURU)[PrayerTimes.ASR] > PrayerTimes.compute(d, shafii)[PrayerTimes.ASR]);
    }

    private static final int[] DAY = {hm("04:59"), hm("06:11"), hm("12:04"), hm("16:21"), hm("17:58"), hm("19:09")};
    private static final int TOMORROW_FAJR = hm("04:59");

    @Test
    public void afternoonIsDhuhrWithAsrNext() {
        PrayerTimes.Status s = PrayerTimes.status(DAY, TOMORROW_FAJR, hm("15:11"));
        assertEquals(PrayerTimes.DHUHR, s.current);
        assertEquals(PrayerTimes.ASR, s.next);
        assertEquals(70, s.minutesUntilNext);
    }

    @Test
    public void theMinuteATimeStartsItIsCurrent() {
        PrayerTimes.Status s = PrayerTimes.status(DAY, TOMORROW_FAJR, hm("16:21"));
        assertEquals(PrayerTimes.ASR, s.current);
        assertEquals(PrayerTimes.MAGHRIB, s.next);
    }

    @Test
    public void afterIshaTheNextIsTomorrowsFajr() {
        PrayerTimes.Status s = PrayerTimes.status(DAY, TOMORROW_FAJR, hm("22:00"));
        assertEquals(PrayerTimes.ISHA, s.current);
        assertEquals(PrayerTimes.FAJR, s.next);
        assertEquals(hm("04:59") + PrayerTimes.MINUTES_PER_DAY, s.nextMinutes);
        assertEquals(PrayerTimes.MINUTES_PER_DAY - hm("22:00") + hm("04:59"), s.minutesUntilNext);
    }

    @Test
    public void beforeFajrTheCurrentIsLastNightsIsha() {
        PrayerTimes.Status s = PrayerTimes.status(DAY, TOMORROW_FAJR, hm("02:00"));
        assertEquals(PrayerTimes.ISHA, s.current);
        assertTrue(s.currentMinutes < 0);
        assertEquals(PrayerTimes.FAJR, s.next);
        assertEquals(hm("04:59") - hm("02:00"), s.minutesUntilNext);
    }

    @Test
    public void homeLineSaysNowAndNext() {
        PrayerTimes.Status s = PrayerTimes.status(DAY, TOMORROW_FAJR, hm("15:11"));
        assertEquals("Now Dhuhr 12:04 PM   ·   Next Asr 4:21 PM, in 1 h 10 min",
                PrayerTimes.homeLine(s, false, Locale.ENGLISH, ENGLISH));
    }

    @Test
    public void homeLineAfterSunriseHasNoNow() {
        PrayerTimes.Status s = PrayerTimes.status(DAY, TOMORROW_FAJR, hm("09:35"));
        assertEquals("Sunrise 6:11 AM   ·   Next Dhuhr 12:04 PM, in 2 h 29 min",
                PrayerTimes.homeLine(s, false, Locale.ENGLISH, ENGLISH));
    }

    @Test
    public void formatsTwelveAndTwentyFourHourTimes() {
        assertEquals("4:59 AM", PrayerTimes.format(hm("04:59"), false, Locale.ENGLISH));
        assertEquals("12:04 PM", PrayerTimes.format(hm("12:04"), false, Locale.ENGLISH));
        assertEquals("12:30 AM", PrayerTimes.format(hm("00:30"), false, Locale.ENGLISH));
        assertEquals("19:09", PrayerTimes.format(hm("19:09"), true, Locale.ENGLISH));
        assertEquals("4:59 AM", PrayerTimes.format(hm("04:59") + PrayerTimes.MINUTES_PER_DAY, false, Locale.ENGLISH));
    }

    @Test
    public void countdownReadsNaturally() {
        assertEquals("in 25 min", PrayerTimes.countdown(25, ENGLISH));
        assertEquals("in 1 h", PrayerTimes.countdown(60, ENGLISH));
        assertEquals("in 1 h 10 min", PrayerTimes.countdown(70, ENGLISH));
    }

    @Test
    public void hijriDateAndOffset() {
        LocalDate d = LocalDate.of(2026, 10, 20);
        String base = PrayerTimes.hijri(d, 0);
        assertTrue(base, base.endsWith(" 1448"));
        assertEquals(PrayerTimes.hijri(d.minusDays(1), 0), PrayerTimes.hijri(d, -1));
    }
}

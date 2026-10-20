package com.gathin.tvhome;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.text.DateFormatSymbols;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Daily prayer times and the Hijri date, computed on the TV with no network. Pure Java, no Android, so it is
 * tested on the JVM against published timetables (PrayerTimesTest).
 *
 * The sun's position comes from the US Naval Observatory's "approximate solar coordinates" (public domain,
 * good to about a minute between 1800 and 2200), and each time from the standard hour-angle formula, the same
 * astronomy most timetables (Aladhan, PrayTimes) use: Fajr and Isha are when the sun is a set angle below the
 * horizon, Sunrise and Maghrib at 0.833 degrees (refraction and the sun's radius), Dhuhr at solar noon, and Asr
 * when an object's shadow is {@code asrFactor} times its length plus its noon shadow (1 Shafi'i, 2 Hanafi).
 * Words shown to people come in through {@link Words}, so the rules here stay testable and the text translatable.
 */
final class PrayerTimes {

    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    static final int COUNT = 6;
    static final int MINUTES_PER_DAY = 24 * 60;

    /** The words around the times, from strings.xml on the TV and plain English in the tests. */
    interface Words {
        /** "Now Dhuhr 12:04 PM". */
        String now(String name, String time);
        /** "Next Asr 4:21 PM, in 1 h 10 min". */
        String next(String name, String time, String countdown);
        String inMinutes(int minutes);
        String inHours(int hours);
        String inHoursMinutes(int hours, int minutes);
        /** "Fajr and Isha 18°" or "Fajr 18°, Isha 17°". */
        String angles(int fajr, int isha);
        /** "Hanafi Asr" or "Shafi'i Asr". */
        String asr(boolean hanafi);
    }

    /** Transliterated names, in index order; the same in every language. */
    static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    /** Arabic names, in index order. */
    static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private static final String[] HIJRI_MONTHS = {
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah"};

    /** The sun's angle below the horizon at sunrise and sunset: refraction plus the sun's radius. */
    private static final double RISE_SET_ANGLE = 0.833;
    /**
     * Beyond about 48.5 degrees the sun may not reach 18 degrees below the horizon in summer, and this method
     * gives no Fajr or Isha; such places need a high-latitude rule this class does not have.
     */
    private static final double MAX_LATITUDE = 48.5;
    /** Julian day of 1 January 2000, 12:00 TT, the epoch of the sun-position series. */
    private static final double J2000 = 2451545.0;

    /** Where and how to calculate. */
    static final class Settings {
        final double latitude;
        final double longitude;
        /** Hours ahead of UTC, e.g. 5.5 for India. */
        final double utcOffsetHours;
        final double fajrAngle;
        final double ishaAngle;
        final int asrFactor;

        Settings(double latitude, double longitude, double utcOffsetHours, double fajrAngle, double ishaAngle,
                 int asrFactor) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.utcOffsetHours = utcOffsetHours;
            this.fajrAngle = fajrAngle;
            this.ishaAngle = ishaAngle;
            this.asrFactor = asrFactor;
        }
    }

    /** A named place with its time zone and calculation method, read from config.xml. */
    static final class Place {
        final String city;
        final double latitude;
        final double longitude;
        final ZoneId zone;
        final int fajrAngle;
        final int ishaAngle;
        final int asrFactor;

        Place(String city, double latitude, double longitude, ZoneId zone, int fajrAngle, int ishaAngle, int asrFactor) {
            this.city = city;
            this.latitude = latitude;
            this.longitude = longitude;
            this.zone = zone;
            this.fajrAngle = fajrAngle;
            this.ishaAngle = ishaAngle;
            this.asrFactor = asrFactor;
        }

        /** Null when a value is missing or out of range, so a typo turns prayer times off instead of showing wrong ones. */
        static Place parse(String city, String latitude, String longitude, String zone,
                           int fajrAngle, int ishaAngle, int asrFactor) {
            try {
                double lat = Double.parseDouble(latitude);
                double lon = Double.parseDouble(longitude);
                if (Math.abs(lat) > MAX_LATITUDE || Math.abs(lon) > 180) return null;
                if (asrFactor < 1 || asrFactor > 2) return null;
                return new Place(city, lat, lon, ZoneId.of(zone), fajrAngle, ishaAngle, asrFactor);
            } catch (NumberFormatException | DateTimeException e) {
                return null;
            }
        }

        /** The calculation settings for {@code date}, with that day's UTC offset (so summer time is right). */
        Settings settingsFor(LocalDate date) {
            double offsetHours = zone.getRules().getOffset(date.atTime(12, 0)).getTotalSeconds() / 3600.0;
            return new Settings(latitude, longitude, offsetHours, fajrAngle, ishaAngle, asrFactor);
        }

        /** "Fajr and Isha 18° · Hanafi Asr". */
        String methodLabel(Words w) {
            return w.angles(fajrAngle, ishaAngle) + " · " + w.asr(asrFactor == 2);
        }
    }

    /** Where the day stands: the latest time that has come and the next one, in minutes from today's midnight. */
    static final class Status {
        /** Index of the latest time that has started; Isha before today's Fajr. */
        final int current;
        /** Minutes from today's midnight; negative when it is yesterday's Isha. */
        final int currentMinutes;
        final int next;
        /** Minutes from today's midnight; above {@link #MINUTES_PER_DAY} when it is tomorrow's Fajr. */
        final int nextMinutes;
        final int minutesUntilNext;

        Status(int current, int currentMinutes, int next, int nextMinutes, int minutesUntilNext) {
            this.current = current;
            this.currentMinutes = currentMinutes;
            this.next = next;
            this.nextMinutes = nextMinutes;
            this.minutesUntilNext = minutesUntilNext;
        }
    }

    private PrayerTimes() {
    }

    /** The six times for {@code date}, in minutes from local midnight, rounded to the nearest minute. */
    static int[] compute(LocalDate date, Settings s) {
        double jDate = julianDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth()) - s.longitude / (15 * 24);
        // First guesses in hours; one refinement pass evaluates the sun at each guess, as PrayTimes does.
        double[] t = {5, 6, 12, 13, 18, 18};
        double fajr = sunAngleTime(jDate, s.latitude, s.fajrAngle, t[FAJR] / 24, true);
        double sunrise = sunAngleTime(jDate, s.latitude, RISE_SET_ANGLE, t[SUNRISE] / 24, true);
        double dhuhr = midDay(jDate, t[DHUHR] / 24);
        double asr = asrTime(jDate, s.latitude, s.asrFactor, t[ASR] / 24);
        double maghrib = sunAngleTime(jDate, s.latitude, RISE_SET_ANGLE, t[MAGHRIB] / 24, false);
        double isha = sunAngleTime(jDate, s.latitude, s.ishaAngle, t[ISHA] / 24, false);

        double shift = s.utcOffsetHours - s.longitude / 15;
        double[] hours = {fajr, sunrise, dhuhr, asr, maghrib, isha};
        int[] minutes = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            minutes[i] = (int) Math.round(fixHour(hours[i] + shift) * 60) % MINUTES_PER_DAY;
        }
        return minutes;
    }

    /**
     * The current and next time at {@code nowMinutes} (minutes from local midnight), given today's times and
     * tomorrow's Fajr. Before today's Fajr the current one is yesterday's Isha, taken as today's Isha minus a day.
     */
    static Status status(int[] today, int tomorrowFajr, int nowMinutes) {
        int current = -1;
        for (int i = 0; i < COUNT; i++) {
            if (today[i] <= nowMinutes) current = i;
        }
        int currentMinutes = current >= 0 ? today[current] : today[ISHA] - MINUTES_PER_DAY;
        if (current < 0) current = ISHA;
        int next;
        int nextMinutes;
        if (current == ISHA && nowMinutes >= today[ISHA]) {
            next = FAJR;
            nextMinutes = tomorrowFajr + MINUTES_PER_DAY;
        } else {
            next = (currentMinutes < 0) ? FAJR : current + 1;
            nextMinutes = today[next];
        }
        return new Status(current, currentMinutes, next, nextMinutes, nextMinutes - nowMinutes);
    }

    /** The Hijri date for {@code date}, moved by {@code offsetDays} for local moon sighting, e.g. "9 Jumada al-Ula 1448". */
    static String hijri(LocalDate date, int offsetDays) {
        HijrahDate h = HijrahDate.from(date.plusDays(offsetDays));
        int month = h.get(ChronoField.MONTH_OF_YEAR);
        return h.get(ChronoField.DAY_OF_MONTH) + " " + HIJRI_MONTHS[month - 1] + " " + h.get(ChronoField.YEAR);
    }

    /**
     * "4:59 AM" style with {@code locale}'s AM/PM, or "16:59" when {@code twentyFourHour}. Minutes may be outside
     * 0..1439; they wrap.
     */
    static String format(int minutes, boolean twentyFourHour, Locale locale) {
        int m = Math.floorMod(minutes, MINUTES_PER_DAY);
        int h = m / 60;
        int min = m % 60;
        if (twentyFourHour) return String.format(Locale.ROOT, "%02d:%02d", h, min);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String[] amPm = DateFormatSymbols.getInstance(locale).getAmPmStrings();
        return String.format(Locale.ROOT, "%d:%02d %s", h12, min, amPm[h < 12 ? 0 : 1]);
    }

    /**
     * The line under the Home clock: "Now Dhuhr 12:04 PM · Next Asr 4:21 PM, in 1 h 10 min". Between Sunrise and
     * Dhuhr no prayer is due, so that part reads "Sunrise 6:11 AM" without "Now".
     */
    static String homeLine(Status s, boolean twentyFourHour, Locale locale, Words w) {
        String currentTime = format(s.currentMinutes, twentyFourHour, locale);
        String current = s.current == SUNRISE ? NAMES[SUNRISE] + " " + currentTime : w.now(NAMES[s.current], currentTime);
        return current + "   ·   "
                + w.next(NAMES[s.next], format(s.nextMinutes, twentyFourHour, locale), countdown(s.minutesUntilNext, w));
    }

    /** "in 1 h 10 min", "in 25 min", "in 1 h". */
    static String countdown(int minutes, Words w) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return w.inMinutes(m);
        if (m == 0) return w.inHours(h);
        return w.inHoursMinutes(h, m);
    }

    // --- sun position (PrayTimes.org) ---

    private static double julianDay(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** Declination (degrees) and equation of time (hours) at Julian day {@code jd}. */
    private static double[] sunPosition(double jd) {
        double d = jd - J2000;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15;
        double eqt = q / 15 - fixHour(ra);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jDate, double dayPortion) {
        double eqt = sunPosition(jDate + dayPortion)[1];
        return fixHour(12 - eqt);
    }

    /** Hours from midnight (UTC-ish, before the longitude shift) when the sun is {@code angle} below the horizon. */
    private static double sunAngleTime(double jDate, double lat, double angle, double dayPortion, boolean beforeNoon) {
        double decl = sunPosition(jDate + dayPortion)[0];
        double noon = midDay(jDate, dayPortion);
        double t = darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))) / 15;
        return noon + (beforeNoon ? -t : t);
    }

    private static double asrTime(double jDate, double lat, int factor, double dayPortion) {
        double decl = sunPosition(jDate + dayPortion)[0];
        double angle = -darccot(factor + dtan(Math.abs(lat - decl)));
        return sunAngleTime(jDate, lat, angle, dayPortion, false);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double darccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    private static double fixAngle(double a) { return a - 360 * Math.floor(a / 360); }
    private static double fixHour(double h) { return h - 24 * Math.floor(h / 24); }
}

package com.gathin.tvhome;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Today's prayer times for the configured place, and the views that show them: the line on Home and the prayer
 * view (prayer_panel.xml) in the Prayer Times page and the Clock screensaver. The maths is in PrayerTimes.
 */
final class PrayerUi {

    private static final int[] CARD_IDS = {R.id.prayerCard0, R.id.prayerCard1, R.id.prayerCard2,
            R.id.prayerCard3, R.id.prayerCard4, R.id.prayerCard5};
    private static final int[] ICONS = {R.drawable.ic_prayer_fajr, R.drawable.ic_prayer_sunrise,
            R.drawable.ic_prayer_dhuhr, R.drawable.ic_prayer_asr, R.drawable.ic_prayer_maghrib, R.drawable.ic_prayer_isha};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

    /** One day's times and where the clock stands in it, in the place's own time zone. */
    static final class Day {
        final PrayerTimes.Place place;
        final LocalDate date;
        final int[] times;
        final PrayerTimes.Status status;
        final String hijri;

        Day(PrayerTimes.Place place, LocalDate date, int[] times, PrayerTimes.Status status, String hijri) {
            this.place = place;
            this.date = date;
            this.times = times;
            this.status = status;
            this.hijri = hijri;
        }
    }

    private PrayerUi() {
    }

    /** Today at the place, or null when prayer times are off. */
    static Day today(Config cfg) {
        PrayerTimes.Place place = cfg.prayerPlace;
        if (place == null) return null;
        ZonedDateTime now = ZonedDateTime.now(place.zone);
        LocalDate date = now.toLocalDate();
        int[] times = PrayerTimes.compute(date, place.settingsFor(date));
        LocalDate tomorrow = date.plusDays(1);
        int tomorrowFajr = PrayerTimes.compute(tomorrow, place.settingsFor(tomorrow))[PrayerTimes.FAJR];
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        PrayerTimes.Status status = PrayerTimes.status(times, tomorrowFajr, nowMinutes);
        return new Day(place, date, times, status, PrayerTimes.hijri(date, cfg.hijriOffsetDays));
    }

    /** Fills in a prayer view (prayer_panel.xml); the next time's card is highlighted. */
    static void bindPanel(View panel, Day d, boolean twentyFourHour) {
        PrayerTimes.Status s = d.status;
        ((TextView) panel.findViewById(R.id.prayerCity)).setText(d.place.city);
        ((TextView) panel.findViewById(R.id.prayerHeadline)).setText(
                PrayerTimes.NAMES[s.next] + " " + PrayerTimes.countdown(s.minutesUntilNext));
        ((TextView) panel.findViewById(R.id.prayerDate)).setText(d.date.format(DATE) + "  ·  " + d.hijri + " AH");
        ((TextView) panel.findViewById(R.id.prayerMethod)).setText(
                d.place.methodLabel() + "  ·  calculated on this TV");
        for (int i = 0; i < PrayerTimes.COUNT; i++) {
            View card = panel.findViewById(CARD_IDS[i]);
            boolean next = i == s.next;
            card.setBackgroundResource(next ? R.drawable.prayer_card_bg_next : R.drawable.prayer_card_bg);
            ((ImageView) card.findViewById(R.id.prayerIcon)).setImageResource(ICONS[i]);
            ((TextView) card.findViewById(R.id.prayerArabic)).setText(PrayerTimes.ARABIC[i]);
            ((TextView) card.findViewById(R.id.prayerName)).setText(PrayerTimes.NAMES[i]);
            ((TextView) card.findViewById(R.id.prayerTime)).setText(PrayerTimes.format(d.times[i], twentyFourHour));
        }
    }
}

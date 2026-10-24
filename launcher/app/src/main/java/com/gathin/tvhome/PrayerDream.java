package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * Screensaver "Prayer": the clock, the Gregorian and Umm al-Qura dates, the next prayer in Home's capsule and the
 * day's five times, over Home's burgundy glow. On every minute (TIME_TICK) the texts are set again and the block
 * fades to a new spot, so nothing burns in. Pick it in Settings, or PRAYER_DREAM=1 build-and-install.sh.
 */
public class PrayerDream extends DreamService {

    private final Random random = new Random();
    private View content;
    private TextView hijri, prayer, day;
    private PrayerTimes table;
    private PrayerTimes.Prayer iconShown;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
            move();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_prayer);
        content = findViewById(R.id.dreamContent);
        hijri = findViewById(R.id.dreamHijri);
        prayer = findViewById(R.id.dreamPrayer);
        day = findViewById(R.id.dreamDay);
        table = PrayerNotice.table(this);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        update();
        content.setAlpha(0f);
        content.post(() -> {
            place();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
    }

    @Override
    public void onDreamingStopped() {
        try {
            unregisterReceiver(tick);
        } catch (IllegalArgumentException ignored) {
            // never registered
        }
        content.animate().cancel();
        super.onDreamingStopped();
    }

    private void update() {
        Resources res = getResources();
        LocalDate today = LocalDate.now();
        hijri.setText(Hijri.format(today, res.getStringArray(R.array.hijri_months)));
        if (table == null) {
            prayer.setVisibility(View.GONE);
            day.setVisibility(View.GONE);
            return;
        }
        Instant now = Instant.now();
        PrayerTimes.Time next = table.next(now);
        prayer.setText(PrayerNotice.line(res, next, now));
        if (next.prayer != iconShown) {
            iconShown = next.prayer;
            prayer.setCompoundDrawablesRelativeWithIntrinsicBounds(PrayerNotice.icon(next.prayer), 0, 0, 0);
        }
        // The five times of the next prayer's day, that one brighter and bold.
        SpannableStringBuilder line = new SpannableStringBuilder();
        List<PrayerTimes.Time> times = table.day(next.at.toLocalDate());
        for (PrayerTimes.Time t : times) {
            if (line.length() > 0) line.append("   ·   ");
            int start = line.length();
            line.append(PrayerNotice.name(res, t.prayer)).append(' ').append(t.clock());
            if (t.prayer == next.prayer) {
                line.setSpan(new ForegroundColorSpan(0xE6FFFFFF), start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                line.setSpan(new StyleSpan(Typeface.BOLD), start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        day.setText(line);
    }

    private void move() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            place();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** A random spot that keeps the whole block on screen. */
    private void place() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        content.setX(maxX * (0.1f + 0.8f * random.nextFloat()));
        content.setY(maxY * (0.1f + 0.8f * random.nextFloat()));
    }
}

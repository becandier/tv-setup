package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * prayer_times on Android: the table from assets, the line under the clock, and the notice over any app at each
 * prayer. No service and nothing polling: one exact alarm for the next prayer at a time, which this receiver turns
 * into a TYPE_APPLICATION_OVERLAY notice for a minute and then replaces with the alarm after it. It is also woken
 * by boot and clock or zone changes, and MainActivity reschedules on every resume. Disabled in the manifest and
 * enabled by MainActivity only while prayer_times is on, so with the switch off nothing here ever runs.
 */
public class PrayerNotice extends BroadcastReceiver {

    private static final String TAG = "PrayerNotice";
    private static final String ACTION_PRAYER = "com.gathin.tvhome.action.PRAYER";
    private static final String ASSET = "prayer_makhachkala.json";
    /** An alarm delivered later than this after its prayer (the TV was in standby) shows nothing. */
    private static final Duration LATE = Duration.ofMinutes(5);
    private static final long SHOW_MS = 60_000L;

    private static PrayerTimes table;
    /** The notice on screen, built on the application context, so holding it leaks no activity. */
    @SuppressLint("StaticFieldLeak")
    private static View shown;

    /**
     * {@code adb shell am broadcast -n com.gathin.tvhome/.PrayerNotice\$Shell}: shows the notice for the nearest
     * prayer now, to check how it looks over an app. Exported only to senders holding DUMP (the adb shell and the
     * system; no installed app can get it), and disabled with the rest while prayer_times is off.
     */
    public static final class Shell extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            PrayerTimes t = table(context);
            if (t != null) show(context, t.next(Instant.now()));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PrayerTimes t = table(context);
        if (t == null) return;
        if (ACTION_PRAYER.equals(intent.getAction())) {
            PrayerTimes.Time now = t.justStarted(Instant.now(), LATE);
            if (now != null) show(context, now);
        }
        // The prayer alarm, boot, or a clock or zone change: always the alarm for the next prayer after now.
        schedule(context);
    }

    /** The table, read once per process; null (and logged) if the asset is missing or broken. */
    static synchronized PrayerTimes table(Context context) {
        if (table != null) return table;
        try (InputStream in = context.getAssets().open(ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            table = PrayerTimes.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            Log.w(TAG, "prayer table unreadable", e);
        }
        return table;
    }

    /** Switches the receivers and the alarm on or off with prayer_times; called by MainActivity.onCreate. */
    static void apply(Context context, boolean on) {
        PackageManager pm = context.getPackageManager();
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        for (Class<?> c : new Class<?>[]{PrayerNotice.class, Shell.class}) {
            ComponentName name = new ComponentName(context, c);
            if (pm.getComponentEnabledSetting(name) != want) {
                pm.setComponentEnabledSetting(name, want, PackageManager.DONT_KILL_APP);
            }
        }
        if (!on) {
            AlarmManager am = context.getSystemService(AlarmManager.class);
            if (am != null) am.cancel(alarmIntent(context));
        }
    }

    /**
     * Sets the one alarm, for the next prayer, replacing any earlier one. Only while the overlay app-op is granted:
     * without it there is nothing to show, and the next return to Home schedules it once it is.
     */
    static void schedule(Context context) {
        PrayerTimes t = table(context);
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (t == null || am == null || !Settings.canDrawOverlays(context)) return;
        long at = t.next(Instant.now()).at.toInstant().toEpochMilli();
        PendingIntent pi = alarmIntent(context);
        // RTC, not RTC_WAKEUP: a TV in standby has no screen to show it on; LATE drops it when the TV wakes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        }
    }

    private static PendingIntent alarmIntent(Context context) {
        Intent intent = new Intent(ACTION_PRAYER).setClass(context, PrayerNotice.class);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static String name(Resources res, PrayerTimes.Prayer p) {
        switch (p) {
            case FAJR: return res.getString(R.string.prayer_fajr);
            case DHUHR: return res.getString(R.string.prayer_dhuhr);
            case ASR: return res.getString(R.string.prayer_asr);
            case MAGHRIB: return res.getString(R.string.prayer_maghrib);
            default: return res.getString(R.string.prayer_isha);
        }
    }

    /** "Asr 14:29 · in 1 h 12 min", for the line under the clock. */
    static String line(Resources res, PrayerTimes t, Instant now) {
        PrayerTimes.Time next = t.next(now);
        long minutes = PrayerTimes.minutesUntil(now, next.at.toInstant());
        String in = minutes < 60 ? res.getString(R.string.prayer_in_minutes, minutes)
                : minutes % 60 == 0 ? res.getString(R.string.prayer_in_hours_whole, minutes / 60)
                : res.getString(R.string.prayer_in_hours, minutes / 60, minutes % 60);
        return res.getString(R.string.prayer_line, name(res, next.prayer), next.clock(), in);
    }

    /**
     * A small pill in the top right corner over whatever is on screen, for a minute. Not focusable and not
     * touchable, so the remote keeps driving the app underneath. Silently nothing without the overlay app-op.
     */
    static void show(Context context, PrayerTimes.Time prayer) {
        if (!Settings.canDrawOverlays(context)) return;
        Context app = context.getApplicationContext();
        WindowManager wm = app.getSystemService(WindowManager.class);
        if (wm == null) return;
        hide(wm);
        Resources res = app.getResources();
        float dp = res.getDisplayMetrics().density;
        TextView text = new TextView(app);
        text.setText(res.getString(R.string.prayer_notice, name(res, prayer.prayer), prayer.clock()));
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(16f);
        text.setSingleLine(true);
        text.setPadding((int) (18 * dp), (int) (10 * dp), (int) (18 * dp), (int) (10 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD9101418);
        bg.setCornerRadius(22 * dp);
        bg.setStroke(Math.max(1, (int) dp), 0x33FFFFFF);
        text.setBackground(bg);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        // Inside the TV's 5% overscan margin, where nothing gets cut off.
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = (int) (48 * dp);
        lp.y = (int) (32 * dp);
        lp.setTitle(TAG);
        try {
            wm.addView(text, lp);
        } catch (RuntimeException e) {
            // The app-op went away between the check and here, or the firmware refuses overlays.
            Log.w(TAG, "notice not shown", e);
            return;
        }
        shown = text;
        // The process stays up while its overlay is visible; if it dies anyway, the window goes with it.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (shown == text) hide(wm);
        }, SHOW_MS);
    }

    private static void hide(WindowManager wm) {
        if (shown == null) return;
        try {
            wm.removeViewImmediate(shown);
        } catch (RuntimeException ignored) {
            // Already gone with its window.
        }
        shown = null;
    }
}

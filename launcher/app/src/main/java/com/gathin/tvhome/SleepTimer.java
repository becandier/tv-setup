package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * sleep_timer: the Sleep pill sets an end time (SleepPlan); one exact alarm fires a minute before it, and a notice
 * counts down over whatever is on screen. Any button on the remote cancels; at zero the TV goes to standby through
 * the remote helper (HomeKeyService or InputKeyService), whose GLOBAL_ACTION_LOCK_SCREEN puts Android 9 and later
 * to sleep as the power button would. No service, nothing polling: the alarm and, for that last minute, the notice.
 */
public class SleepTimer extends BroadcastReceiver {

    private static final String TAG = "SleepTimer";
    private static final String ACTION_WARN = "com.gathin.tvhome.action.SLEEP_WARN";
    private static final String PREFS = "sleep_timer", END = "end", MINUTES = "minutes";

    /** A running remote helper, set by the service itself; the one way to put the TV to sleep without root. */
    @SuppressLint("StaticFieldLeak")
    static volatile AccessibilityService helper;

    /** The countdown on screen, on the application context; null when none. */
    @SuppressLint("StaticFieldLeak")
    private static TextView warning;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * {@code adb shell am broadcast -n com.gathin.tvhome/.SleepTimer\$Shell --ei minutes 1}: starts a timer of that
     * many minutes (1 shows the warning at once). Only senders holding DUMP (the adb shell, the system).
     */
    public static final class Shell extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            start(context, Math.max(1, intent.getIntExtra("minutes", 1)));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_WARN.equals(intent.getAction())) return;
        long end = end(context);
        long now = System.currentTimeMillis();
        PowerManager pm = context.getSystemService(PowerManager.class);
        // A warning delivered after the TV went to standby on its own, or long after the end: nothing to do.
        if (end == 0 || now > end + SleepPlan.WARN_MS || (pm != null && !pm.isInteractive())) {
            clear(context);
            return;
        }
        warn(context, end);
    }

    static void apply(Context context, boolean on) {
        PackageManager pm = context.getPackageManager();
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        for (Class<?> c : new Class<?>[]{SleepTimer.class, Shell.class}) {
            ComponentName name = new ComponentName(context, c);
            if (pm.getComponentEnabledSetting(name) != want) {
                pm.setComponentEnabledSetting(name, want, PackageManager.DONT_KILL_APP);
            }
        }
        if (!on) clear(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The end time, or 0 with no timer (or one already past). */
    static long end(Context context) {
        long end = prefs(context).getLong(END, 0);
        return end > System.currentTimeMillis() - SleepPlan.WARN_MS ? end : 0;
    }

    /** OK on the pill: the next step (SleepPlan.next), from off when the last timer has run out. */
    static void step(Context context) {
        int minutes = end(context) == 0 ? 0 : prefs(context).getInt(MINUTES, 0);
        int next = SleepPlan.next(minutes);
        if (next == 0) clear(context);
        else start(context, next);
    }

    static void start(Context context, int minutes) {
        long end = System.currentTimeMillis() + minutes * 60_000L;
        prefs(context).edit().putLong(END, end).putInt(MINUTES, minutes).apply();
        hideWarning(context);
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) return;
        long at = Math.max(System.currentTimeMillis(), end - SleepPlan.WARN_MS);
        PendingIntent pi = alarmIntent(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        }
    }

    /** No timer: the alarm, the saved end and any warning on screen go. */
    static void clear(Context context) {
        prefs(context).edit().remove(END).remove(MINUTES).apply();
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(alarmIntent(context));
        hideWarning(context);
    }

    private static PendingIntent alarmIntent(Context context) {
        Intent intent = new Intent(ACTION_WARN).setClass(context, SleepTimer.class);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** True while the countdown is on screen. */
    static boolean warning() {
        return warning != null;
    }

    /** Cancelled with a press: Home calls this on resume, since the Home button never reaches the notice. */
    static void cancelByUser(Context context) {
        if (warning == null) return;
        clear(context);
        Toast.makeText(context.getApplicationContext(), R.string.sleep_cancelled, Toast.LENGTH_SHORT).show();
    }

    /**
     * The countdown over any app, top right. Focusable, unlike the prayer notice, so the remote's next press comes
     * here and cancels instead of reaching the app. Without the overlay app-op there is no warning: the TV still
     * sleeps at the end, as a timer the owner set should.
     */
    private static void warn(Context context, long end) {
        Context app = context.getApplicationContext();
        if (!Settings.canDrawOverlays(app)) {
            handler.postDelayed(() -> finish(app, end), Math.max(0, end - System.currentTimeMillis()));
            return;
        }
        WindowManager wm = app.getSystemService(WindowManager.class);
        if (wm == null) return;
        hideWarning(app);
        Resources res = app.getResources();
        float dp = res.getDisplayMetrics().density;
        TextView text = new TextView(app) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) cancelByUser(app);
                return true;
            }
        };
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(16f);
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_sleep, 0, 0, 0);
        text.setCompoundDrawablePadding((int) (10 * dp));
        text.setPadding((int) (18 * dp), (int) (10 * dp), (int) (18 * dp), (int) (10 * dp));
        text.setFocusable(true);
        text.setFocusableInTouchMode(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6101418);
        bg.setCornerRadius(22 * dp);
        bg.setStroke(Math.max(1, (int) dp), 0x33FFFFFF);
        text.setBackground(bg);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = (int) (48 * dp);
        lp.y = (int) (32 * dp);
        lp.setTitle(TAG);
        try {
            wm.addView(text, lp);
        } catch (RuntimeException e) {
            Log.w(TAG, "warning not shown", e);
            handler.postDelayed(() -> finish(app, end), Math.max(0, end - System.currentTimeMillis()));
            return;
        }
        text.requestFocus();
        warning = text;
        Runnable count = new Runnable() {
            @Override
            public void run() {
                if (warning != text) return;
                long left = SleepPlan.secondsLeft(System.currentTimeMillis(), end);
                if (left <= 0) {
                    finish(app, end);
                    return;
                }
                text.setText(res.getString(R.string.sleep_warning, left));
                handler.postDelayed(this, 1000);
            }
        };
        count.run();
    }

    /** The end: still the same timer (not cancelled or replaced), so the TV goes to standby. */
    private static void finish(Context app, long end) {
        if (prefs(app).getLong(END, 0) != end) return;
        clear(app);
        sleep(app);
    }

    /** Standby through the remote helper; without one the timer cannot put the TV to sleep and says so. */
    static void sleep(Context app) {
        AccessibilityService service = helper;
        boolean done = false;
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            done = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }
        if (!done) {
            Log.w(TAG, "no remote helper to sleep with");
            Toast.makeText(app, R.string.sleep_no_helper, Toast.LENGTH_LONG).show();
        }
    }

    private static void hideWarning(Context context) {
        TextView shown = warning;
        warning = null;
        if (shown == null) return;
        WindowManager wm = context.getApplicationContext().getSystemService(WindowManager.class);
        try {
            if (wm != null) wm.removeViewImmediate(shown);
        } catch (RuntimeException ignored) {
            // Already gone with its window.
        }
    }
}

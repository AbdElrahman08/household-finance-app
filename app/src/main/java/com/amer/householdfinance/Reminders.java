package com.amer.householdfinance;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/**
 * Daily reminder: once a day at the chosen time it checks whether any bill or instalment
 * is due today or tomorrow, and whether anything was logged today, and posts one notification.
 * The page keeps the settings and the list of unpaid bills up to date through the bridge.
 */
public class Reminders extends BroadcastReceiver {
    static final String PREFS = "hf";
    static final String ACTION = "com.amer.householdfinance.REMIND";
    private static final String CHANNEL = "reminders";

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, Reminders.class).setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am == null) return;
        am.cancel(pi);
        try {
            JSONObject o = new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("rem", "{}"));
            if (!o.optBoolean("on") && !o.optBoolean("bills")) return;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, o.optInt("h", 21));
            cal.set(Calendar.MINUTE, o.optInt("m", 0));
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            schedule(c);
            return;
        }
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject o = new JSONObject(p.getString("rem", "{}"));
            Calendar now = Calendar.getInstance();
            int day = now.get(Calendar.DAY_OF_MONTH);
            int last = now.getActualMaximum(Calendar.DAY_OF_MONTH);
            int tomorrow = day == last ? 1 : day + 1;
            String ym = String.format(Locale.US, "%04d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1);
            String today = ym + String.format(Locale.US, "-%02d", day);
            StringBuilder sb = new StringBuilder();

            if (o.optBoolean("bills") && ym.equals(o.optString("ym"))) {
                JSONArray list = o.optJSONArray("list");
                if (list != null) {
                    for (int k = 0; k < list.length(); k++) {
                        JSONObject b = list.getJSONObject(k);
                        int d = Math.min(b.optInt("day"), last);
                        String when = d == day ? "النهارده" : d == tomorrow && tomorrow != 1 ? "بكرة" : null;
                        if (when == null) continue;
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(b.optString("n")).append(" مستحق ").append(when)
                          .append(" (").append(Math.round(b.optDouble("a"))).append(" ج.م)");
                    }
                }
            }
            if (o.optBoolean("on") && !today.equals(p.getString("logged", ""))) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("لسه ماسجلتش مصاريف النهارده");
            }
            if (sb.length() > 0) notify(c, sb.toString());
        } catch (Exception ignored) {
        }
    }

    private static void notify(Context c, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "تذكيرات", NotificationManager.IMPORTANCE_DEFAULT));
            nb = new Notification.Builder(c, CHANNEL);
        } else {
            nb = new Notification.Builder(c);
        }
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String first = text.split("\n")[0];
        nb.setSmallIcon(R.drawable.ic_notify)
          .setContentTitle("مالية البيت")
          .setContentText(first)
          .setStyle(new Notification.BigTextStyle().bigText(text))
          .setContentIntent(open)
          .setAutoCancel(true);
        nm.notify(7, nb.build());
    }
}

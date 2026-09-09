package pl.mindupgrade.android;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;

final class MorningAlarm {
    static final String DAILY = "pl.mindupgrade.android.MORNING";
    static final String TEST = "pl.mindupgrade.android.TEST_MORNING";
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("morning", Context.MODE_PRIVATE); }
    static boolean allowed(Context c) { return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }
    static long next(long now) {
        ZonedDateTime current = Instant.ofEpochMilli(now).atZone(ZoneId.of("Europe/Warsaw"));
        ZonedDateTime next = current.toLocalDate().atTime(6, 1).atZone(current.getZone());
        if (!next.isAfter(current)) next = current.toLocalDate().plusDays(1).atTime(6, 1).atZone(current.getZone());
        return next.toInstant().toEpochMilli();
    }
    static PendingIntent pending(Context c, boolean test) {
        return PendingIntent.getBroadcast(c, test ? 602 : 601, new Intent(c, MorningReceiver.class).setAction(test ? TEST : DAILY), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void schedule(Context c, boolean test) {
        if (!allowed(c)) throw new SecurityException("Alarm permission missing");
        long at = test ? System.currentTimeMillis() + 60000 : next(System.currentTimeMillis());
        PendingIntent show = PendingIntent.getActivity(c, 601, new Intent(c, AlarmSettingsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pending(c, test));
        prefs(c).edit().putLong(test ? "testAt" : "nextAt", at).apply();
    }
    static void cancel(Context c) {
        prefs(c).edit().putBoolean("enabled", false).remove("nextAt").remove("testAt").apply();
        c.getSystemService(AlarmManager.class).cancel(pending(c, false));
        c.getSystemService(AlarmManager.class).cancel(pending(c, true));
        c.stopService(new Intent(c, MorningSpeechService.class));
    }
}

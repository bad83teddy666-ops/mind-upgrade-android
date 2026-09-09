package pl.mindupgrade.android;

import android.content.*;

public final class MorningReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        boolean test = MorningAlarm.TEST.equals(action);
        boolean daily = MorningAlarm.DAILY.equals(action);
        if (test || daily) {
            if (daily && !MorningAlarm.prefs(c).getBoolean("enabled", false)) return;
            long expected = MorningAlarm.prefs(c).getLong(test ? "testAt" : "nextAt", 0);
            MorningAlarm.prefs(c).edit().remove(test ? "testAt" : "nextAt").apply();
            if (daily) reschedule(c);
            // Skip cancelled, duplicate or very late deliveries rather than speaking hours later.
            long delay = System.currentTimeMillis() - expected;
            if (expected == 0 || delay < -2000 || delay > 10 * 60000) return;
            try { c.startForegroundService(new Intent(c, MorningSpeechService.class)); }
            catch (RuntimeException error) { MorningAlarm.prefs(c).edit().putString("result", "Telefon zablokował odtwarzanie. Otwórz budzik i wykonaj test ponownie.").apply(); }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) reschedule(c);
    }
    private void reschedule(Context c) {
        if (MorningAlarm.prefs(c).getBoolean("enabled", false) && MorningAlarm.allowed(c)) {
            try { MorningAlarm.schedule(c, false); } catch (SecurityException ignored) { }
        }
    }
}

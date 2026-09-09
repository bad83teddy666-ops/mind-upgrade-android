package pl.mindupgrade.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

public final class AlarmSettingsActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24, 48, 24, 24);
        TextView title = new TextView(this); title.setText("Budzik Mind Upgrade · 6:01"); title.setTextSize(24); root.addView(title);
        TextView description = new TextView(this); description.setText("Powitanie codziennie o 6:01 czasu polskiego. Bez mikrofonu i bez klucza AI. Ustaw słyszalną głośność alarmów. Najpierw wykonaj test z zablokowanym ekranem.\nTelefon musi być włączony; po wymuszonym zatrzymaniu aplikacji otwórz ją ponownie. Po restarcie odblokuj telefon."); description.setTextSize(16); root.addView(description);
        status = new TextView(this); status.setTextSize(16); root.addView(status);
        button(root, "1. Zezwól na powiadomienia", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 201);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        button(root, "2. Zezwól na dokładny budzik", () -> {
            if (Build.VERSION.SDK_INT >= 31 && !MorningAlarm.allowed(this))
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            else showStatus();
        });
        button(root, "Test za minutę", () -> arm(true));
        button(root, "Włącz codziennie o 6:01", () -> arm(false));
        button(root, "Wyłącz budzik i test", () -> { MorningAlarm.cancel(this); showStatus(); });
        button(root, "Wróć do Mind Upgrade", this::finish);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }
    private void button(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(v -> action.run()); root.addView(button);
    }
    private void arm(boolean test) {
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled() || !MorningAlarm.allowed(this)) {
            status.setText("Najpierw udziel obu zgód przyciskami powyżej. Budzik nie został ustawiony."); return;
        }
        try {
            MorningAlarm.schedule(this, test);
            if (!test) MorningAlarm.prefs(this).edit().putBoolean("enabled", true).apply();
            status.setText(test ? "Test zaplanowany za minutę. Teraz zablokuj ekran i posłuchaj. Test nie włącza codziennego budzika." : "Włączono codzienne powitanie o 6:01 czasu polskiego.");
        } catch (SecurityException error) { status.setText("Brak zgody na dokładny alarm. Udziel jej i spróbuj ponownie."); }
    }
    private void showStatus() {
        boolean enabled = MorningAlarm.prefs(this).getBoolean("enabled", false);
        status.setText((enabled ? "Budzik 6:01 włączony." : "Budzik codzienny wyłączony.")
            + (MorningAlarm.allowed(this) ? "" : " Brak zgody na dokładny alarm.")
            + "\n" + MorningAlarm.prefs(this).getString("result", "Test nie został jeszcze potwierdzony na telefonie."));
    }
    @Override protected void onResume() { super.onResume(); showStatus(); }
}

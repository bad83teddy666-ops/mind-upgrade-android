package pl.mindupgrade.android;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Offline wake-word acceptance screen. No WebView, network or AI. */
public final class BibiTestActivity extends Activity {
    private TextView status;
    private TextView result;
    private boolean resumed;
    private boolean pendingStart;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() {
            render();
            if (resumed) ui.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        int p = Math.round(24 * getResources().getDisplayMetrics().density);
        root.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(p+i.getSystemWindowInsetLeft(), p+i.getSystemWindowInsetTop(),
                p+i.getSystemWindowInsetRight(), p+i.getSystemWindowInsetBottom()); return i;
        });
        TextView title = new TextView(this);
        title.setText("Mind Upgrade · Bibi"); title.setTextSize(26); title.setTextColor(Color.CYAN);
        root.addView(title);
        result = new TextView(this); result.setTextColor(Color.GREEN); result.setTextSize(23); root.addView(result);
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(16);
        status.setPadding(0,p,0,p); root.addView(status);
        add(root,"1. Wybierz jako asystenta", () -> {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                RoleManager roles = getSystemService(RoleManager.class);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 102); return;
                }
            }
            startActivity(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS));
        });
        add(root,"2. Włącz nasłuch Bibi", this::requestStart);
        add(root,"Wyłącz nasłuch", () -> { pendingStart=false; BibiAssistantService.enable(this,false); render(); });
        TextView help = new TextView(this);
        help.setText("Po włączeniu poczekaj na przygotowanie modelu. Wyjdź na pulpit, wygasz ekran i powiedz bi-bi, potem zrób krótką pauzę.\n\nTest pokazuje tylko potwierdzenie. Nie otwiera przeglądarki ani rozmowy z AI. Nie odblokowuje telefonu. Nasłuch zużywa baterię i ma stałe powiadomienie z przyciskiem Wyłącz.");
        help.setTextSize(16); help.setTextColor(Color.LTGRAY); help.setPadding(0,p,0,0); root.addView(help);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.setFillViewport(true); scroll.addView(root);
        setContentView(scroll);
        showDetection(getIntent());
    }
    private void add(LinearLayout root, String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setOnClickListener(v -> action.run()); root.addView(b);
    }
    private void requestStart() {
        if (!BibiAssistantService.selected(this)) {
            result.setText("Najpierw wybierz Mind Upgrade Bibi Test jako asystenta."); return;
        }
        pendingStart = true;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},100); return;
        }
        startWhenReady();
    }
    private void startWhenReady() {
        if (!pendingStart || !resumed) return;
        pendingStart=false;
        BibiAssistantService.enable(this,true);
        result.setText("Test włączony. Przygotowuję nasłuch…");
        render();
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if (request == 100) {
            if (grants.length == 0 || grants[0] != PackageManager.PERMISSION_GRANTED) {
                pendingStart=false; result.setText("Brak zgody na mikrofon. Nasłuch nie został włączony.");
            } else startWhenReady();
        }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); showDetection(intent); }
    private void showDetection(Intent intent) {
        boolean detected = intent.getBooleanExtra("bibi_detected",false);
        setShowWhenLocked(detected); setTurnScreenOn(detected);
        if (detected) {
            getSharedPreferences("bibi",0).edit().putLong("last_opened",System.currentTimeMillis()).apply();
            result.setText("Usłyszałem Bibi — aplikacja otwarta.");
        }
    }
    private void render() {
        if (status == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("bibi",0);
        long at = prefs.getLong("last_detected",0);
        String last = at == 0 ? "Jeszcze nie wykryto hasła." : "Ostatnie wykrycie: " + android.text.format.DateFormat.format("HH:mm:ss",at);
        long opened = prefs.getLong("last_opened",0);
        status.setText(BibiAssistantService.state(this)+"\n"+last+"\nLiczba wykryć: "+prefs.getInt("detections",0)
            +(at>opened ? "\nHasło wykryto, ale ekran aplikacji nie potwierdził otwarcia." : ""));
    }
    @Override protected void onResume() {
        super.onResume(); resumed=true; BibiAssistantService.visible(true);
        ui.removeCallbacks(update); ui.post(update); startWhenReady();
    }
    @Override protected void onPause() { resumed=false; ui.removeCallbacks(update); super.onPause(); }
    @Override protected void onStop() { BibiAssistantService.visible(false); super.onStop(); }
    @Override protected void onDestroy() { ui.removeCallbacksAndMessages(null); super.onDestroy(); }
}

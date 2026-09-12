package pl.mindupgrade.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.service.voice.VoiceInteractionService;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;
import org.vosk.android.RecognitionListener;
import org.json.*;
import java.io.*;

/** User-enabled, local-only wake detection. Never sends ambient audio to a server. */
public final class BibiAssistantService extends VoiceInteractionService {
    static BibiAssistantService instance;
    static boolean activityVisible;
    static java.util.function.Consumer<String> stateListener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Model model;
    private Recognizer decoder;
    private SpeechService microphone;
    private boolean ready, loading, destroyed, foreground;
    private int generation;
    private String error;
    private PowerManager.WakeLock cpu;
    static boolean selected(Context c) {
        return isActiveService(c, new ComponentName(c, BibiAssistantService.class));
    }
    static boolean enabled(Context c) { return c.getSharedPreferences("bibi", 0).getBoolean("enabled", false); }
    static void enable(Context c, boolean value) {
        c.getSharedPreferences("bibi", 0).edit().putBoolean("enabled", value).apply();
        if (instance != null) { instance.error = null; instance.refresh(); }
    }
    static void visible(boolean value) {
        activityVisible = value;
        if (instance != null) instance.refresh();
    }
    static String state(Context c) {
        if (!selected(c)) return "Wybierz Mind Upgrade jako asystenta Androida.";
        if (instance != null && instance.error != null) return instance.error;
        if (!enabled(c)) return "Bibi wyłączone.";
        if (instance == null || !instance.ready) return "Asystent jest uruchamiany. Wróć za chwilę.";
        if (instance.error != null) return instance.error;
        if (instance.loading) return "Przygotowuję Bibi…";
        if (instance.model == null) return "Przygotowuję model Bibi…";
        return activityVisible ? "Bibi włączone · czuwa po wyjściu z aplikacji." : "Bibi czuwa.";
    }
    @Override public void onReady() { super.onReady(); instance = this; ready = true; refresh(); }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "STOP_BIBI".equals(intent.getAction())) enable(this, false);
        return START_NOT_STICKY;
    }
    private void notification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("bibi", "Czuwanie Bibi", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, BibiTestActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, BibiAssistantService.class).setAction("STOP_BIBI"), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "bibi").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Mind Upgrade · Bibi").setContentText(text).setContentIntent(open)
            .setOngoing(true).addAction(new Notification.Action.Builder(null, "Wyłącz", stop).build()).build();
        startForeground(42, n); foreground = true;
    }
    private void refresh() {
        if (!ready || destroyed) return;
        stopMicrophone();
        if (!enabled(this) || !selected(this)) {
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false; return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Zezwól na mikrofon i włącz Bibi ponownie."); return;
        }
        if (error != null) return;
        try { notification(activityVisible ? "Czuwanie wróci po wyjściu z aplikacji" : "Słucham hasła Bibi na urządzeniu"); }
        catch (RuntimeException e) { fail("Android zatrzymał mikrofon. Otwórz aplikację i włącz Bibi ponownie."); return; }
        if (model == null) {
            if (loading) return;
            loading = true;
            new Thread(() -> {
                try {
                    File folder = new File(getFilesDir(), "bibi-model-v1");
                    if (!new File(folder, "complete").exists()) {
                        copyAssets("bibi-model", folder);
                        new File(folder, "complete").createNewFile();
                    }
                    Model loaded = new Model(folder.getAbsolutePath());
                    main.post(() -> { loading = false; if (destroyed) loaded.close(); else { model = loaded; refresh(); if (stateListener != null) stateListener.accept(state(this)); } });
                } catch (Exception | LinkageError e) {
                    main.post(() -> { loading = false; if (!destroyed) fail("Nie udało się załadować Bibi. Wyłącz i włącz czuwanie."); });
                }
            }, "bibi-model").start();
            return;
        }
        if (activityVisible) return;
        try {
            // English bee bee approximates the Polish pronunciation of Bibi.
            decoder = new Recognizer(model, 16000, "[\"bee bee\", \"[unk]\"]");
            decoder.setWords(true);
            microphone = new SpeechService(decoder, 16000);
            final int session = generation;
            cpu = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MindUpgrade:Bibi");
            keepAwake(session);
            microphone.startListening(new RecognitionListener() {
                public void onPartialResult(String result) {}
                public void onResult(String result) { detected(result, session); }
                public void onFinalResult(String result) { detected(result, session); }
                public void onError(Exception e) { if (session == generation) fail("Mikrofon Bibi jest niedostępny. Włącz czuwanie ponownie."); }
                public void onTimeout() { if (session == generation) fail("Czuwanie przerwane. Włącz Bibi ponownie."); }
            });
        } catch (Exception | LinkageError e) { fail("Nie udało się rozpocząć czuwania Bibi. Włącz je ponownie."); }
    }
    private void detected(String json, int session) {
        if (session != generation || activityVisible || !enabled(this)) return;
        try {
            JSONObject result = new JSONObject(json);
            if (!"bee bee".equals(result.optString("text").trim())) return;
            JSONArray words = result.optJSONArray("result");
            if (words == null || words.length() != 2) return;
            for (int i=0; i<2; i++) if (words.getJSONObject(i).optDouble("conf", 0) < 0.8) return;
            getSharedPreferences("bibi", 0).edit()
                .putLong("last_detected", System.currentTimeMillis())
                .putInt("detections", getSharedPreferences("bibi", 0).getInt("detections", 0) + 1).apply();
            stopMicrophone();
            Bundle args = new Bundle(); args.putBoolean("bibi", true);
            showSession(args, 0);
            main.postDelayed(() -> { if (!activityVisible && !destroyed) refresh(); }, 15000);
        } catch (RuntimeException e) { fail("Nie udało się otworzyć rozmowy. Otwórz Mind Upgrade."); }
        catch (JSONException ignored) { }
    }
    private void keepAwake(int session) {
        if (session != generation || cpu == null || destroyed) return;
        if (cpu.isHeld()) cpu.release();
        cpu.acquire(10 * 60 * 1000L);
        main.postDelayed(() -> keepAwake(session), 9 * 60 * 1000L);
    }
    private void stopMicrophone() {
        generation++;
        if (cpu != null) { if (cpu.isHeld()) cpu.release(); cpu = null; }
        if (microphone != null) { microphone.stop(); microphone.shutdown(); microphone = null; }
        if (decoder != null) { decoder.close(); decoder = null; }
    }
    private void fail(String text) {
        error = text; stopMicrophone();
        if (stateListener != null) stateListener.accept(text);
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        getSharedPreferences("bibi", 0).edit().putBoolean("enabled", false).apply();
    }
    private void copyAssets(String name, File target) throws IOException {
        String[] children = getAssets().list(name);
        if (children != null && children.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("mkdir");
            for (String child : children) copyAssets(name + "/" + child, new File(target, child));
        } else {
            try (InputStream in = getAssets().open(name); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
        }
    }
    @Override public void onShutdown() { cleanup(); super.onShutdown(); }
    @Override public void onDestroy() { cleanup(); super.onDestroy(); }
    private void cleanup() {
        destroyed = true; ready = false; main.removeCallbacksAndMessages(null); stopMicrophone();
        if (model != null) { model.close(); model = null; }
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        if (instance == this) instance = null;
    }
}

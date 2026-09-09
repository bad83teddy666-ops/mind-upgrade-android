package pl.mindupgrade.android;

import android.app.*;
import android.content.Intent;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.Locale;

public final class MorningSpeechService extends Service {
    private TextToSpeech voice;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private android.os.PowerManager.WakeLock wake;
    private AudioFocusRequest focus;
    private boolean closed;
    private static final String CHANNEL = "morning_greeting";
    private static final String TEXT = "Dzień dobry, Piotrek. Zaczynamy nowy dzień. Jeden mały krok dziś jest więcej wart niż wielki plan na kiedyś.";
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Budzik głosowy", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); manager.createNotificationChannel(channel);
        PendingIntent stop = PendingIntent.getService(this, 603, new Intent(this, MorningSpeechService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(this, 604, new Intent(this, AlarmSettingsActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Mind Upgrade · poranne powitanie").setContentText("Dzień dobry. Dotknij STOP, aby wyciszyć.")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setContentIntent(open)
            .addAction(new Notification.Action.Builder(null, "STOP", stop).build()).build();
        startForeground(601, notification);
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MindUpgrade:morning");
        wake.acquire(60000);
        handler.postDelayed(() -> finish("Odtwarzanie zakończone limitem czasu."), 45000);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "STOP".equals(intent.getAction())) { finish("Powitanie zatrzymane."); return START_NOT_STICKY; }
        if (voice != null || closed) return START_NOT_STICKY;
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(change -> { if (change < 0) handler.post(() -> finish("Powitanie przerwane przez inne audio.")); }).build();
        if (getSystemService(AudioManager.class).requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            finish("Brak dostępu do dźwięku. Spróbuj testu po zakończeniu rozmowy telefonicznej."); return START_NOT_STICKY;
        }
        voice = new TextToSpeech(this, result -> handler.post(() -> {
            if (closed) return;
            if (result != TextToSpeech.SUCCESS || voice.setLanguage(Locale.forLanguageTag("pl-PL")) < TextToSpeech.LANG_AVAILABLE) {
                finish("Brak polskiego głosu. Pobierz polski głos w ustawieniach syntezy mowy telefonu."); return;
            }
            voice.setAudioAttributes(attributes); voice.setSpeechRate(0.9f); voice.setPitch(0.8f);
            voice.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { handler.post(() -> finish("Silnik głosu zakończył powitanie. Potwierdź, czy było słyszalne.")); }
                @Override public void onError(String id) { handler.post(() -> finish("Błąd odtwarzania polskiego głosu.")); }
            });
            if (voice.speak(TEXT, TextToSpeech.QUEUE_FLUSH, null, "morning") == TextToSpeech.ERROR) finish("Nie udało się rozpocząć powitania.");
        }));
        return START_NOT_STICKY;
    }
    private void finish(String result) {
        if (closed) return;
        MorningAlarm.prefs(this).edit().putString("result", result).apply();
        close(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private void close() {
        closed = true; handler.removeCallbacksAndMessages(null);
        if (voice != null) { voice.stop(); voice.shutdown(); voice = null; }
        if (focus != null) getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
        if (wake != null && wake.isHeld()) wake.release();
    }
    @Override public void onDestroy() { close(); super.onDestroy(); }
}

package pl.mindupgrade.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.*;
import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicitly started microphone foreground service. Audio stays on device. */
public final class BibiWakeService extends Service {
    static volatile BibiWakeService instance;
    static volatile String status = "Nasłuch wyłączony.";
    static volatile int level;
    static volatile boolean listening;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile AudioRecord recorder;
    private Model model;
    private PowerManager.WakeLock cpu;
    private boolean destroyed;
    private final Runnable renew = new Runnable() { public void run() {
        if (destroyed || cpu == null) return;
        cpu.acquire(10*60*1000L); main.postDelayed(this,9*60*1000L);
    }};
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onCreate() { super.onCreate(); instance=this; }
    static void start(Context c) {
        if(c.getSharedPreferences("mind-audio",0).getBoolean("headset-only",false)){status="Bibi wstrzymane w trybie tylko słuchawki.";return;}
        c.startForegroundService(new Intent(c,BibiWakeService.class).setAction("START"));
    }
    static void stop(Context c) {
        c.getSharedPreferences("bibi",0).edit().putBoolean("enabled",false).apply();
        c.stopService(new Intent(c,BibiWakeService.class));
        status="Nasłuch wyłączony.";
    }
    static void pause() {
        if (instance != null) {
            instance.cancelCapture(); status="Nasłuch wstrzymany podczas rozmowy. Wróć do aplikacji, aby wznowić.";
            instance.notifyState(status);
        }
    }
    @Override public int onStartCommand(Intent i,int flags,int id) {
        if (i == null || "STOP".equals(i.getAction())) { stop(this); return START_NOT_STICKY; }
        if(getSharedPreferences("mind-audio",0).getBoolean("headset-only",false)){cancelCapture();status="Bibi wstrzymane w trybie tylko słuchawki.";stopSelf();return START_NOT_STICKY;}
        try {
            notifyState("Przygotowuję mikrofon i model Bibi…");
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Brak zgody na mikrofon");
            getSharedPreferences("bibi",0).edit().putBoolean("enabled",true).apply();
            cancelCapture();
            status="Przygotowuję mikrofon i model Bibi…";
            cpu=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MindUpgrade:Bibi");
            cpu.setReferenceCounted(false); renew.run();
            int token=generation.get(); worker.execute(() -> capture(token));
        } catch (RuntimeException e) { fail("Nie można włączyć mikrofonu: "+e.getClass().getSimpleName()+". Sprawdź uprawnienie Mikrofon i systemowy dostęp do mikrofonu."); }
        return START_NOT_STICKY;
    }
    private void notifyState(String text) {
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("bibi","Nasłuch Bibi",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,BibiHomeActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,BibiWakeService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"bibi").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mind Upgrade · Bibi").setContentText(text).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Wyłącz",stop).build()).build();
        startForeground(42,n);
    }
    @android.annotation.SuppressLint("MissingPermission") // Runtime permission checked before submission; revocation is caught below.
    private void capture(int token) {
        AudioRecord local=null; Recognizer decoder=null; boolean detected=false;
        try {
            if (model == null) {
                File folder=new File(getFilesDir(),"bibi-model-v1");
                if (!new File(folder,"complete").exists()) { copyAssets("bibi-model",folder); new File(folder,"complete").createNewFile(); }
                model=new Model(folder.getAbsolutePath());
            }
            if (token!=generation.get()) return;
            decoder=new Recognizer(model,16000,"[\"bee bee\",\"[unk]\"]"); decoder.setWords(true);
            int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if (min<=0) throw new IOException("Nieobsługiwany format mikrofonu");
            local=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,6400));
            if (local.getState()!=AudioRecord.STATE_INITIALIZED) throw new IOException("Mikrofon nie został zainicjalizowany");
            recorder=local;
            if(token!=generation.get()) return;
            local.startRecording();
            if(local.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Mikrofon nie nagrywa");
            listening=true; status="Nasłuch aktywny — powiedz bi-bi i zrób pauzę.";
            main.post(() -> { if(token==generation.get()) notifyState("Słucham Bibi lokalnie, także przy wygaszonym ekranie"); });
            short[] samples=new short[1600];
            while(token==generation.get()) {
                int n=local.read(samples,0,samples.length);
                if(n<0) throw new IOException("Odczyt mikrofonu: "+n);
                if(n==0) continue;
                int peak=0; for(int k=0;k<n;k++) peak=Math.max(peak,Math.abs((int)samples[k]));
                level=Math.min(100,peak*100/12000);
                if(decoder.acceptWaveForm(samples,n)) {
                    JSONObject result=new JSONObject(decoder.getResult());
                    JSONArray words=result.optJSONArray("result");
                    if("bee bee".equals(result.optString("text").trim()) && words!=null && words.length()==2
                        && words.getJSONObject(0).optDouble("conf",0)>=0.8 && words.getJSONObject(1).optDouble("conf",0)>=0.8) { detected=true; break; }
                }
            }
        } catch(Exception | LinkageError e) {
            main.post(() -> { if(token==generation.get()) fail("Błąd nasłuchu: "+e.getClass().getSimpleName()+" · "+e.getMessage()); });
        } finally {
            if(local!=null) { try { local.stop(); } catch(IllegalStateException ignored) {} local.release(); }
            if(recorder==local) recorder=null;
            if(decoder!=null) decoder.close();
            if(token==generation.get()) { listening=false; level=0; }
        }
        if(detected) main.post(() -> { if(token==generation.get()) onWake(); });
    }
    private void onWake() {
        cancelCapture();
        android.content.SharedPreferences p=getSharedPreferences("bibi",0);
        p.edit().putLong("last_detected",System.currentTimeMillis()).putInt("detections",p.getInt("detections",0)+1).apply();
        if (BibiHomeActivity.visible) {
            status="Usłyszałem Bibi — otwieram panel.";
            startActivity(new Intent(this,BibiHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("bibi_detected",true));
        } else if(BibiAssistantService.openFromWake()) {
            status="Usłyszałem Bibi — czekam na otwarcie ekranu.";
        } else {
            status="Usłyszałem Bibi, ale systemowa usługa asystenta nie jest aktywna. Wybierz Mind Upgrade w ustawieniach asystenta.";
        }
        notifyState(status);
        main.postDelayed(() -> {
            if(!destroyed && p.getLong("last_opened",0)<p.getLong("last_detected",0)) {
                status="Bibi wykryte, ale Android nie otworzył ekranu. Sprawdź domyślnego asystenta.";
                notifyState(status);
                // Existing foreground microphone service retains its permission context.
                // Re-arm after failed assistant handoff instead of leaving capture stopped.
                if(p.getBoolean("enabled",false))onStartCommand(new Intent().setAction("START"),0,0);
            }
        },4000);
    }
    private void cancelCapture() {
        generation.incrementAndGet(); listening=false; level=0;
        AudioRecord r=recorder; if(r!=null) try {r.stop();} catch(IllegalStateException ignored) {}
        main.removeCallbacks(renew);
        if(cpu!=null) { if(cpu.isHeld())cpu.release(); cpu=null; }
    }
    private void fail(String text) {
        cancelCapture(); status=text;
        getSharedPreferences("bibi",0).edit().putBoolean("enabled",false).apply();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private void copyAssets(String name,File target) throws IOException {
        String[] children=getAssets().list(name);
        if(children!=null && children.length>0) {
            if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Nie można przygotować modelu");
            for(String child:children)copyAssets(name+"/"+child,new File(target,child));
        } else try(InputStream in=getAssets().open(name); OutputStream out=new FileOutputStream(target)) {
            byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1)out.write(b,0,n);
        }
    }
    @Override public void onDestroy() {
        destroyed=true; cancelCapture(); main.removeCallbacksAndMessages(null);
        worker.execute(() -> { if(model!=null) {model.close();model=null;} }); worker.shutdown();
        if(instance==this)instance=null; stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
}


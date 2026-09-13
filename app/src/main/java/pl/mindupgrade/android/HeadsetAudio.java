package pl.mindupgrade.android;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.util.Base64;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.function.Consumer;

/** Fail-closed, app-scoped communication routing. Never changes system volume. */
final class HeadsetAudio {
    private final Activity activity;
    private final AudioManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Consumer<JSONObject> emit;
    private MediaPlayer player;
    private File clip;
    private AudioDeviceInfo selected;
    private boolean strict, active;
    private boolean manualPhone;
    private int generation;
    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            if (selected != null) for (AudioDeviceInfo d : removed) if (d.getId() == selected.getId()) { lost("Słuchawki rozłączone. Rozmowa zatrzymana."); break; }
        }
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            if (active && !manualPhone) for (AudioDeviceInfo d : added) if (headset(d)) { setStrict(true);lost("Podłączono słuchawki. Włącz rozmowę, aby użyć ich mikrofonu.");break; }
        }
    };
    private final AudioManager.AudioRecordingCallback recording = new AudioManager.AudioRecordingCallback() {
        @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            if (Build.VERSION.SDK_INT < 31 || !active || !strict || selected == null) return;
            for (AudioRecordingConfiguration c : configs) if (!c.isClientSilenced() && c.getAudioDevice() != null && !headset(c.getAudioDevice())) {
                lost("Android wybrał mikrofon telefonu. Rozmowa zatrzymana; połącz słuchawki ponownie."); return;
            }
        }
    };
    static boolean enabled(Activity a) { return a.getSharedPreferences("mind-audio",0).getBoolean("headset-only",false); }
    static boolean headset(AudioDeviceInfo d) {
        if (d == null) return false;
        int t=d.getType();
        return t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t==AudioDeviceInfo.TYPE_WIRED_HEADSET || t==AudioDeviceInfo.TYPE_USB_HEADSET || (Build.VERSION.SDK_INT>=31 && t==AudioDeviceInfo.TYPE_BLE_HEADSET);
    }
    HeadsetAudio(Activity a, Consumer<JSONObject> output) {
        activity=a; emit=output; manager=a.getSystemService(AudioManager.class);strict=enabled(a);manualPhone=a.getSharedPreferences("mind-audio",0).getBoolean("manual-phone",false);
        manager.registerAudioDeviceCallback(devices,handler);
        manager.registerAudioRecordingCallback(recording,handler);
    }
    private void setStrict(boolean value) {
        strict=value;activity.getSharedPreferences("mind-audio",0).edit().putBoolean("headset-only",value).apply();
        BibiAssistantService.visible(true);
    }
    private JSONObject state(String note) {
        JSONObject data=new JSONObject();try{data.put("type","audio-state").put("strict",strict).put("ready",!strict || (selected!=null && Build.VERSION.SDK_INT>=31 && same(manager.getCommunicationDevice(),selected))).put("label",selected==null?"Telefon":selected.getProductName().toString()).put("note",note);}catch(Exception ignored){}return data;
    }
    private boolean same(AudioDeviceInfo a,AudioDeviceInfo b){return a!=null&&b!=null&&a.getId()==b.getId();}
    private void emitState(String note){emit.accept(state(note));}
    private void lost(String note) {
        generation++;stopPlayback();selected=null;
        JSONObject message=state(note);try{message.put("ready",false).put("lost",true);}catch(Exception ignored){}emit.accept(message);
    }
    void prepare(String mode, Consumer<JSONObject> done) {
        active=true;
        if ("phone".equals(mode)) {release();active=true;manualPhone=true;activity.getSharedPreferences("mind-audio",0).edit().putBoolean("manual-phone",true).apply();setStrict(false);done.accept(state("Rozmowa przez telefon."));return;}
        if ("headset".equals(mode)) {manualPhone=false;activity.getSharedPreferences("mind-audio",0).edit().putBoolean("manual-phone",false).apply();setStrict(true);}
        if(manualPhone){done.accept(state("Rozmowa przez telefon."));return;}
        if(Build.VERSION.SDK_INT<31){if(strict)lost("Tryb tylko słuchawki wymaga Androida 12 lub nowszego.");done.accept(state(""));return;}
        if(activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            if(strict){activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},103);lost("Zezwól na urządzenia w pobliżu i włącz rozmowę ponownie.");}
            done.accept(state(""));return;
        }
        AudioDeviceInfo target=null;
        for(AudioDeviceInfo device:manager.getAvailableCommunicationDevices())if(headset(device)){target=device;break;}
        if(target==null){if(strict)lost("Połącz słuchawki z mikrofonem i włącz rozmowę.");done.accept(state(""));return;}
        setStrict(true);final AudioDeviceInfo wanted=target;final int current=++generation;
        manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if(!manager.setCommunicationDevice(wanted)){lost("Nie udało się przełączyć na słuchawki.");done.accept(state(""));return;}
        final long deadline=SystemClock.elapsedRealtime()+6000;
        Runnable check=new Runnable(){public void run(){
            if(current!=generation){done.accept(state("Przełączanie zatrzymane."));return;}
            if(same(manager.getCommunicationDevice(),wanted)){selected=wanted;done.accept(state("Mikrofon i dźwięk: słuchawki."));return;}
            if(SystemClock.elapsedRealtime()>=deadline){lost("Słuchawki nie są gotowe. Włącz rozmowę ponownie.");done.accept(state(""));return;}
            handler.postDelayed(this,100);
        }};check.run();
    }
    void play(String id,String encoded) {
        stopPlayback();
        try{
            if(!strict||selected==null||Build.VERSION.SDK_INT<31||!same(manager.getCommunicationDevice(),selected))throw new IllegalStateException("Połącz słuchawki przed odtwarzaniem.");
            if(encoded.length()>8000000)throw new IllegalArgumentException("Nagranie jest zbyt długie.");
            clip=File.createTempFile("mind-voice-",".mp3",activity.getCacheDir());
            try(FileOutputStream out=new FileOutputStream(clip)){out.write(Base64.decode(encoded,Base64.DEFAULT));}
            MediaPlayer next=new MediaPlayer();player=next;
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            if(!next.setPreferredDevice(selected))throw new IllegalStateException("Nie można wybrać słuchawek.");
            next.setDataSource(clip.getAbsolutePath());
            next.addOnRoutingChangedListener((AudioRouting.OnRoutingChangedListener)router->{if(player==next && next.isPlaying() && !headset(next.getRoutedDevice()))lost("Dźwięk słuchawek przerwany. Rozmowa zatrzymana.");},handler);
            next.setOnPreparedListener(p->{if(player!=p)return;if(selected==null){stopPlayback();return;}p.start();event(id,"playing","");});
            next.setOnCompletionListener(p->{if(player==p){stopPlayback();event(id,"ended","");}});
            next.setOnErrorListener((p,what,extra)->{if(player==p){stopPlayback();event(id,"error","Nie udało się odtworzyć głosu w słuchawkach.");}return true;});
            next.prepareAsync();
        }catch(Exception e){stopPlayback();event(id,"error",e.getMessage());}
    }
    private void event(String id,String kind,String note){try{emit.accept(new JSONObject().put("type","audio-playback").put("id",id).put("event",kind).put("note",note));}catch(Exception ignored){}}
    void stopPlayback(){if(player!=null){player.release();player=null;}if(clip!=null){clip.delete();clip=null;}}
    void release(){active=false;generation++;stopPlayback();selected=null;if(Build.VERSION.SDK_INT>=31)manager.clearCommunicationDevice();manager.setMode(AudioManager.MODE_NORMAL);}
    void close(){release();manager.unregisterAudioDeviceCallback(devices);manager.unregisterAudioRecordingCallback(recording);}
}

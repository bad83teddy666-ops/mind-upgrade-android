package pl.mindupgrade.android;
import android.content.*;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;

/** Android-bound launcher only. Microphone lifetime belongs to BibiWakeService. */
public final class BibiAssistantService extends VoiceInteractionService {
    static BibiAssistantService instance;
    static java.util.function.Consumer<String> stateListener;
    static boolean selected(Context c) { return isActiveService(c,new ComponentName(c,BibiAssistantService.class)); }
    static boolean enabled(Context c) { return c.getSharedPreferences("bibi",0).getBoolean("enabled",false); }
    static void enable(Context c,boolean enabled) { if(enabled) BibiWakeService.start(c); else BibiWakeService.stop(c); }
    static void visible(boolean value) { /* Voice role no longer gates microphone acquisition. */ }
    static String state(Context c) { return BibiWakeService.status; }
    static boolean openFromWake() {
        if(instance==null || !selected(instance))return false;
        try {Bundle args=new Bundle();args.putBoolean("bibi",true);instance.showSession(args,0);return true;}
        catch(RuntimeException e){return false;}
    }
    @Override public void onReady(){super.onReady();instance=this;}
    @Override public void onLaunchVoiceAssistFromKeyguard(){openFromWake();}
    @Override public void onShutdown(){if(instance==this)instance=null;super.onShutdown();}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
}

package pl.mindupgrade.android;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
public final class BibiSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args) {
        return new VoiceInteractionSession(this) {
            @Override public void onPrepareShow(Bundle args, int flags) { super.onPrepareShow(args, flags); setUiEnabled(false); }
            @Override public void onShow(Bundle args, int flags) {
                super.onShow(args, flags);
                try { startAssistantActivity(new Intent(getContext(), BibiHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("bibi_detected", args != null && args.getBoolean("bibi", false)));
                } catch (RuntimeException e) { BibiWakeService.status="Android odrzucił otwarcie aplikacji: "+e.getClass().getSimpleName(); }
                hide();
            }
        };
    }
}

package pl.mindupgrade.android;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/** Required assistant speech endpoint; delegates dictation to an installed provider. */
public final class BibiRecognitionService extends RecognitionService {
    private SpeechRecognizer delegate;
    private Callback active;
    private interface Reply { void run() throws RemoteException; }
    private void send(Callback target, Reply reply) {
        if (target != active) return;
        try { reply.run(); } catch (RemoteException ignored) { release(); }
    }
    @Override protected void onStartListening(Intent intent, Callback callback) {
        if (active != null) {
            try { callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); } catch (RemoteException ignored) {}
            return;
        }
        active = callback;
        try {
            ComponentName provider = null;
            for (ResolveInfo r : getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0)) {
                if (r.serviceInfo != null && !getPackageName().equals(r.serviceInfo.packageName)) {
                    provider = new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name); break;
                }
            }
            if (provider == null) { send(callback, () -> callback.error(SpeechRecognizer.ERROR_CLIENT)); release(); return; }
            delegate = SpeechRecognizer.createSpeechRecognizer(this, provider);
            delegate.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) { send(callback, () -> callback.readyForSpeech(b)); }
                public void onBeginningOfSpeech() { send(callback, callback::beginningOfSpeech); }
                public void onRmsChanged(float v) { send(callback, () -> callback.rmsChanged(v)); }
                public void onBufferReceived(byte[] b) { send(callback, () -> callback.bufferReceived(b)); }
                public void onEndOfSpeech() { send(callback, callback::endOfSpeech); }
                public void onError(int e) { send(callback, () -> callback.error(e)); release(); }
                public void onResults(Bundle b) { send(callback, () -> callback.results(b)); release(); }
                public void onPartialResults(Bundle b) { send(callback, () -> callback.partialResults(b)); }
                public void onEvent(int e, Bundle b) { }
            });
            delegate.startListening(intent);
        } catch (RuntimeException e) { send(callback, () -> callback.error(SpeechRecognizer.ERROR_CLIENT)); release(); }
    }
    @Override protected void onStopListening(Callback callback) { if (delegate != null) delegate.stopListening(); }
    @Override protected void onCancel(Callback callback) { release(); }
    @Override public void onDestroy() { release(); super.onDestroy(); }
    private void release() { active = null; if (delegate != null) { delegate.destroy(); delegate = null; } }
}

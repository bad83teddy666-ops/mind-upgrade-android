package pl.mindupgrade.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://kupiec-techniczny-piotr.bad83teddy666.chatgpt.site";
    private WebView web;
    private TextView status;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean foreground;
    private JavaScriptReplyProxy reply;
    private String activeId;
    private boolean test;
    private Runnable permissionAction;
    private Runnable resumeAction;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(6, 13, 22));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setPadding(16, 8, 16, 8);
        status.setText("Mind Upgrade · test Android · Bibi jeszcze nieaktywne");
        root.addView(status);
        LinearLayout bar = new LinearLayout(this);
        button(bar, "Test głosu", () -> {
            cancelRecognition();
            test = true;
            activeId = "test";
            withMicrophone(this::listen);
        });
        button(bar, "Odśwież", () -> web.reload());
        button(bar, "W Chrome", () -> external(Uri.parse(ORIGIN)));
        root.addView(bar);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(6, 13, 22));
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (trusted(uri)) return false;
                if (request.isForMainFrame()) external(uri);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                cancelRecognition();
                if (tts != null) tts.stop();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) status.setText("Nie udało się otworzyć strony. Sprawdź internet i wybierz Odśwież.");
            }
        });
        tts = new TextToSpeech(this, result -> {
            if (isDestroyed()) return;
            if (result == TextToSpeech.SUCCESS) {
                int language = tts.setLanguage(Locale.forLanguageTag("pl-PL"));
                ttsReady = language >= TextToSpeech.LANG_AVAILABLE;
            }
            if (!ttsReady) status.setText("Brak polskiego głosu. Dodaj język polski w ustawieniach syntezy mowy Androida.");
        });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addWebMessageListener(web, "MindNative", Set.of(ORIGIN),
                (view, message, sourceOrigin, isMainFrame, responder) -> {
                    if (!isMainFrame || !trusted(sourceOrigin) || !foreground) return;
                    try { handle(new JSONObject(message.getData()), responder); }
                    catch (Exception error) { status.setText("Nieprawidłowe polecenie głosowe aplikacji."); }
                });
            try (java.io.InputStream stream = getAssets().open("voice-bridge.js")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                String script = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                WebViewCompat.addDocumentStartJavaScript(web, script, Set.of(ORIGIN));
            } catch (java.io.IOException error) {
                status.setText("Nie udało się uruchomić modułu głosu.");
            }
        } else {
            status.setText("Zaktualizuj Android System WebView, aby używać przycisku MÓW w aplikacji.");
        }
        web.loadUrl(ORIGIN);
    }

    private boolean trusted(Uri uri) {
        return "https".equals(uri.getScheme())
            && "kupiec-techniczny-piotr.bad83teddy666.chatgpt.site".equals(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null;
    }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }
    private void external(Uri uri) {
        if (!"https".equals(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (android.content.ActivityNotFoundException error) { status.setText("Nie znaleziono przeglądarki."); }
    }
    private void handle(JSONObject message, JavaScriptReplyProxy responder) {
        switch (message.optString("type")) {
            case "listen":
                cancelRecognition();
                test = false;
                activeId = message.optString("id");
                reply = responder;
                withMicrophone(this::listen);
                break;
            case "stop":
                if (message.optString("id").equals(activeId) && recognizer != null) recognizer.stopListening();
                break;
            case "abort":
                if (message.optString("id").equals(activeId)) cancelRecognition();
                break;
            case "speak":
                speak(message.optString("text"), (float) message.optDouble("rate", 1),
                    (float) message.optDouble("pitch", 1));
                break;
            case "silence":
                if (tts != null) tts.stop();
                break;
            default: break;
        }
    }
    private void withMicrophone(Runnable action) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action.run();
        else {
            permissionAction = action;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 100) return;
        Runnable action = permissionAction;
        permissionAction = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && action != null) {
            if (foreground) action.run();
            else resumeAction = action;
        } else fail("not-allowed", "Zezwól na mikrofon i naciśnij przycisk ponownie.");
    }
    private void listen() {
        if (!foreground || activeId == null) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("service-not-allowed", "Brak systemowej usługi rozpoznawania mowy."); return;
        }
        if (tts != null) tts.stop();
        final String session = activeId;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            private boolean current() { return session.equals(activeId); }
            @Override public void onReadyForSpeech(Bundle params) { if (current()) status.setText("Słucham…"); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { if (current()) status.setText("Rozpoznaję wypowiedź…"); }
            @Override public void onError(int error) {
                if (!current()) return;
                fail(error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "not-allowed" : "no-speech",
                    "Nie udało się rozpoznać wypowiedzi (" + error + "). Naciśnij MÓW ponownie.");
            }
            @Override public void onResults(Bundle results) {
                if (!current()) return;
                ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (values == null || values.isEmpty()) { fail("no-speech", "Nie usłyszałem wypowiedzi."); return; }
                String text = values.get(0);
                status.setText(test ? "Usłyszałem: " + text : "Wypowiedź przekazana do Mind Upgrade.");
                if (test) speak("Usłyszałem: " + text, 0.9f, 0.85f);
                else emit("result", "text", text);
                finishRecognition();
            }
            @Override public void onPartialResults(Bundle results) {}
            @Override public void onEvent(int type, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        try { recognizer.startListening(intent); }
        catch (RuntimeException error) { fail("audio-capture", "Nie udało się uruchomić mikrofonu."); }
    }
    private void speak(String text, float rate, float pitch) {
        if (!ttsReady) { status.setText("Polski głos nie jest jeszcze gotowy."); return; }
        tts.setSpeechRate(Math.max(0.5f, Math.min(2, rate)));
        tts.setPitch(Math.max(0.5f, Math.min(2, pitch)));
        tts.speak(text.substring(0, Math.min(text.length(), TextToSpeech.getMaxSpeechInputLength())),
            TextToSpeech.QUEUE_FLUSH, null, "mind-reply");
    }
    private void emit(String type, String key, String value) {
        if (reply == null || activeId == null) return;
        try {
            JSONObject payload = new JSONObject().put("type", type).put("id", activeId);
            if (key != null) payload.put(key, value);
            reply.postMessage(payload.toString());
        } catch (Exception ignored) { /* The page may have navigated away. */ }
    }
    private void fail(String code, String description) {
        status.setText(description);
        emit("error", "error", code);
        finishRecognition();
    }
    private void finishRecognition() {
        emit("end", null, null);
        activeId = null;
        reply = null;
        permissionAction = null;
        resumeAction = null;
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
    }
    private void cancelRecognition() {
        if (recognizer != null) recognizer.cancel();
        finishRecognition();
    }
    @Override protected void onResume() {
        super.onResume(); foreground = true;
        if (web != null) web.onResume();
        Runnable action = resumeAction;
        resumeAction = null;
        if (action != null) action.run();
    }
    @Override protected void onPause() {
        foreground = false;
        // A runtime permission dialog also pauses the Activity. Preserve only its pending request.
        if (permissionAction == null) cancelRecognition();
        if (tts != null) tts.stop();
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override protected void onStop() {
        cancelRecognition();
        super.onStop();
    }
    @Override protected void onDestroy() {
        cancelRecognition();
        if (tts != null) tts.shutdown();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}

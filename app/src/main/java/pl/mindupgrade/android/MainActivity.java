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
    private HeadsetAudio headsetAudio;
    private android.widget.ProgressBar progress;
    private LinearLayout recovery;
    private TextView recoveryText;
    private boolean pageFailed;
    private boolean pageLoading;
    private final Runnable loadTimeout = () -> {
        if (pageLoading) showRecovery("Ładowanie trwa zbyt długo. Sprawdź internet i spróbuj ponownie.");
    };
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
    private boolean wakeRequested;
    private android.webkit.PermissionRequest webPermission;
    private final android.os.Handler wakeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int wakeTries;
    private final Runnable wakePoll = this::startWakeConversation;

    private void configureBibi() {
        if (!BibiAssistantService.selected(this)) {
            new android.app.AlertDialog.Builder(this).setTitle("Uruchamianie przez Bibi")
                .setMessage("Wybierz Mind Upgrade jako domyślnego asystenta Androida. Potem wróć i włącz Bibi. Czuwanie używa mikrofonu lokalnie i zwiększa zużycie baterii.")
                .setPositiveButton("Wybierz asystenta", (dialog, which) -> {
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
                        if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                            startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT), 102);
                            return;
                        }
                    }
                    startActivity(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS));
                }).setNegativeButton("Anuluj", null).show();
            return;
        }
        if (BibiAssistantService.enabled(this)) {
            BibiAssistantService.enable(this, false); status.setText("Bibi wyłączone."); return;
        }
        withMicrophone(() -> {
            BibiAssistantService.enable(this, true);
            status.setText(BibiAssistantService.state(this));
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        });
    }
    private void startWakeConversation() {
        if (!wakeRequested || !foreground || web == null) return;
        if (++wakeTries > 40) {
            wakeRequested = false;
            status.setText("Bibi otworzyło aplikację. Zaloguj się i naciśnij Włącz rozmowę."); return;
        }
        if (trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) {
            web.evaluateJavascript("(() => { const buttons = [...document.querySelectorAll('button')]; const norm = b => b.textContent.trim().toLocaleUpperCase('pl'); if (buttons.some(b => norm(b) === 'ZAKOŃCZ ROZMOWĘ')) return true; const b = buttons.find(b => norm(b) === 'WŁĄCZ ROZMOWĘ' && !b.disabled); if (!b) return false; b.click(); return true; })()", result -> {
                if ("true".equals(result)) { wakeRequested = false; status.setText("Bibi · włączam rozmowę…"); }
                else if (wakeRequested && foreground) wakeHandler.postDelayed(wakePoll, 500);
            });
        } else wakeHandler.postDelayed(wakePoll, 500);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getBooleanExtra("start_conversation", false)) {
            wakeRequested = true; wakeTries = 0;
            wakeHandler.removeCallbacks(wakePoll); wakeHandler.post(wakePoll);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wakeRequested = getIntent().getBooleanExtra("start_conversation", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Mind Upgrade");
        title.setTextColor(Color.rgb(103, 216, 255));
        title.setTextSize(16);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button menu = new Button(this);
        menu.setText("⋮");
        menu.setTextColor(Color.rgb(103, 216, 255));
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setContentDescription("Menu aplikacji: Bibi, odświeżanie i test głosu");
        menu.setOnClickListener(this::showAppMenu);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(bar);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        status.setText(BibiAssistantService.state(this));
        status.setVisibility(android.view.View.GONE);
        root.addView(status);
        progress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        android.widget.FrameLayout content = new android.widget.FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        content.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));
        recovery = new LinearLayout(this);
        recovery.setOrientation(LinearLayout.VERTICAL);
        recovery.setGravity(android.view.Gravity.CENTER);
        recovery.setPadding(dp(24), dp(24), dp(24), dp(24));
        recovery.setBackgroundColor(Color.BLACK);
        recoveryText = new TextView(this);
        recoveryText.setTextColor(Color.WHITE);
        recoveryText.setTextSize(18);
        recoveryText.setGravity(android.view.Gravity.CENTER);
        recovery.addView(recoveryText);
        Button retry = new Button(this);
        retry.setText("Spróbuj ponownie");
        retry.setOnClickListener(v -> web.loadUrl(ORIGIN));
        recovery.addView(retry);
        Button browser = new Button(this);
        browser.setText("Otwórz Mind Upgrade w przeglądarce");
        browser.setOnClickListener(v -> external(Uri.parse(ORIGIN)));
        recovery.addView(browser);
        content.addView(recovery, new android.widget.FrameLayout.LayoutParams(-1, -1));
        recovery.setVisibility(android.view.View.GONE);
        setContentView(root);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value < 100 && !pageFailed ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) {
                if (!foreground || !trusted(request.getOrigin()) || !trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) { request.deny(); return; }
                boolean audio = java.util.Arrays.asList(request.getResources()).contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE);
                if (!audio) { request.deny(); return; }
                webPermission = request;
                withMicrophone(() -> {
                    if (webPermission == request && foreground && trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) {
                        request.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                        webPermission = null;
                    }
                });
            }
            @Override public void onPermissionRequestCanceled(android.webkit.PermissionRequest request) {
                if (webPermission == request) webPermission = null;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageLoading = false;
                wakeHandler.removeCallbacks(loadTimeout);
                CookieManager.getInstance().flush();
                if (pageFailed) return;
                wakeHandler.removeCallbacks(wakePoll); wakeHandler.post(wakePoll);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (trusted(uri)) return false;
                if(request.isForMainFrame() && "https".equals(uri.getScheme()) && (uri.getPort()==-1 || uri.getPort()==443) && uri.getUserInfo()==null && Set.of("chatgpt.com","auth.openai.com","auth0.openai.com","auth.chatgpt.com").contains(uri.getHost())) return false;
                if (request.isForMainFrame()) external(uri);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageFailed = false;
                pageLoading = true;
                recovery.setVisibility(android.view.View.GONE);
                status.setVisibility(android.view.View.GONE);
                progress.setVisibility(android.view.View.VISIBLE);
                wakeHandler.removeCallbacks(loadTimeout);
                wakeHandler.postDelayed(loadTimeout, 30000);
                cancelRecognition();
                if (tts != null) tts.stop();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) showRecovery("Nie udało się otworzyć Mind Upgrade. Sprawdź połączenie z internetem.");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                      android.webkit.WebResourceResponse response) {
                if (!request.isForMainFrame()) return;
                int code = response.getStatusCode();
                if (code == 401 || code == 403) {
                    // Keep the server's sign-in page available; never bypass its access checks.
                    status.setText("Zaloguj się na stronie poniżej. Logowanie w Chrome nie loguje aplikacji. Jeśli logowanie jest blokowane, użyj menu → Otwórz w przeglądarce.");
                    status.setVisibility(android.view.View.VISIBLE);
                } else if (code >= 400) {
                    showRecovery("Mind Upgrade jest chwilowo niedostępne (" + code + "). Spróbuj ponownie.");
                }
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
        headsetAudio = new HeadsetAudio(this, this::emitAudio);
        web.loadUrl(ORIGIN);
    }

    private boolean trusted(Uri uri) {
        return "https".equals(uri.getScheme())
            && "kupiec-techniczny-piotr.bad83teddy666.chatgpt.site".equals(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private void showRecovery(String message) {
        pageFailed = true;
        pageLoading = false;
        wakeHandler.removeCallbacks(loadTimeout);
        progress.setVisibility(android.view.View.GONE);
        recoveryText.setText(message);
        recovery.setVisibility(android.view.View.VISIBLE);
    }
    private void showAppMenu(android.view.View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, BibiAssistantService.enabled(this) ? "Wyłącz Bibi" : "Włącz Bibi");
        menu.getMenu().add(0, 2, 1, "Odśwież Mind Upgrade");
        menu.getMenu().add(0, 3, 2, "Test głosu");
        menu.getMenu().add(0, 4, 3, "Pokaż / ukryj stan aplikacji");
        menu.getMenu().add(0, 5, 4, "Otwórz w przeglądarce");
        menu.getMenu().add(0, 6, 5, "Tylko słuchawki");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    status.setVisibility(android.view.View.VISIBLE);
                    configureBibi(); break;
                case 2: web.loadUrl(ORIGIN); break;
                case 3:
                    status.setVisibility(android.view.View.VISIBLE);
                    cancelRecognition(); test = true; activeId = "test";
                    withMicrophone(this::listen); break;
                case 4:
                    status.setVisibility(status.getVisibility() == android.view.View.VISIBLE
                        ? android.view.View.GONE : android.view.View.VISIBLE); break;
                case 5: external(Uri.parse(ORIGIN)); break;
                case 6: headsetAudio.prepare("headset", this::emitAudio); break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    private void external(Uri uri) {
        if (!"https".equals(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (android.content.ActivityNotFoundException error) { status.setText("Nie znaleziono przeglądarki."); }
    }
    private void emitAudio(JSONObject data) {
        if (web == null || !foreground || !trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) return;
        web.evaluateJavascript("window.__mindAudioEvent?.(" + data.toString() + ")", null);
    }
    private void handle(JSONObject message, JavaScriptReplyProxy responder) {
        switch (message.optString("type")) {
            case "audio-prepare":
                headsetAudio.prepare(message.optString("mode", "auto"), data -> {
                    try { data.put("requestId", message.optString("id")); responder.postMessage(data.toString()); } catch (Exception ignored) { }
                }); break;
            case "audio-play":
                headsetAudio.play(message.optString("id"),message.optString("audio")); break;
            case "audio-stop": headsetAudio.stopPlayback(); break;
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
        } else {
            if (webPermission != null) { webPermission.deny(); webPermission = null; }
            fail("not-allowed", "Zezwól na mikrofon i naciśnij przycisk ponownie.");
        }
    }
    private void listen() {
        if (!foreground || activeId == null) return;
        if (HeadsetAudio.enabled(this)) { fail("audio-capture", "W trybie słuchawek użyj przycisku MÓW w aplikacji."); return; }
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
        if (HeadsetAudio.enabled(this)) { status.setText("W trybie słuchawek użyj głosu Onyx w aplikacji."); return; }
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
        BibiAssistantService.stateListener = text -> status.setText(text);
        BibiAssistantService.visible(true);
        if (status != null) status.setText(BibiAssistantService.state(this));
        wakeHandler.removeCallbacks(wakePoll); wakeHandler.post(wakePoll);
        if (web != null) web.onResume();
        Runnable action = resumeAction;
        resumeAction = null;
        if (action != null) action.run();
    }
    @Override protected void onPause() {
        foreground = false;
        BibiAssistantService.stateListener = null;
        wakeHandler.removeCallbacks(wakePoll);
        // A runtime permission dialog also pauses the Activity. Preserve only its pending request.
        if (permissionAction == null) cancelRecognition();
        if (tts != null) tts.stop();
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override protected void onStop() {
        cancelRecognition();
        if (web != null && trusted(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) {
            web.evaluateJavascript("[...document.querySelectorAll('button')].find(b => b.textContent.trim().toLocaleUpperCase('pl') === 'ZAKOŃCZ ROZMOWĘ')?.click(); window.__mindReleaseMicrophone?.();", ignored -> { if (!foreground) BibiAssistantService.visible(false); });
        }
        wakeHandler.postDelayed(() -> { if (!foreground) BibiAssistantService.visible(false); }, 1000);
        if(headsetAudio != null) headsetAudio.release();
        super.onStop();
    }
    @Override protected void onDestroy() {
        wakeHandler.removeCallbacksAndMessages(null);
        cancelRecognition();
        if (tts != null) tts.shutdown();
        if(headsetAudio != null) headsetAudio.close();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}


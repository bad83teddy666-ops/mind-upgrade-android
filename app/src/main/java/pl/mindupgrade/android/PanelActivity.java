package pl.mindupgrade.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.browser.customtabs.CustomTabsIntent;

/** Keep Sites sign-in and the authenticated panel in the same browser session. */
public final class PanelActivity extends Activity {
    private static final Uri HOME = Uri.parse("https://kupiec-techniczny-piotr.bad83teddy666.chatgpt.site/");
    private TextView message;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(this);
        title.setText("Mind Upgrade");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(103, 216, 255));
        root.addView(title);
        message = new TextView(this);
        message.setText("Panel i logowanie otwierają się razem w przeglądarce. Po zalogowaniu korzystaj z Mind Upgrade w tym samym oknie.\n\nJeśli zobaczysz Not Found, wróć tutaj i wybierz Otwórz Mind Upgrade.\n\nTryb panelu korzysta z głosu wersji webowej. Bibi w tej wersji pozostaje wyłączone, aby nie zajmowało mikrofonu.");
        message.setTextColor(Color.WHITE);
        message.setTextSize(16);
        message.setPadding(0, padding, 0, padding);
        root.addView(message);
        Button open = new Button(this);
        open.setText("Otwórz Mind Upgrade");
        open.setOnClickListener(v -> openPanel());
        root.addView(open);
        setContentView(root);
        if (state == null) openPanel();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPanel();
    }

    private void openPanel() {
        // Never forward an OAuth URL/code from WebView or copy browser cookies.
        // Start at the canonical home URL; Sites owns the complete sign-in flow.
        BibiAssistantService.enable(this, false);
        CustomTabsIntent tab = new CustomTabsIntent.Builder()
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setShowTitle(true)
            .build();
        try {
            tab.launchUrl(this, HOME);
        } catch (android.content.ActivityNotFoundException error) {
            message.setText("Nie znaleziono przeglądarki. Zainstaluj lub włącz przeglądarkę i naciśnij Otwórz Mind Upgrade ponownie.");
        }
    }
}

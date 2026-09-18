package pl.mindupgrade.android;
import android.content.Context;
/** Consumer build has no background assistant or offline wake-word engine. */
final class BibiAssistantService {
    static java.util.function.Consumer<String> stateListener;
    static boolean selected(Context context) { return false; }
    static boolean enabled(Context context) { return false; }
    static void enable(Context context, boolean value) { }
    static void visible(boolean value) { }
    static String state(Context context) { return "Mind Upgrade · testy kont użytkowników"; }
}

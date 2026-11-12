package android.content;
/** Minimal host fixture, excluded from the APK build. */
public class Context {
    private final SharedPreferences preferences;
    public Context(SharedPreferences preferences) { this.preferences = preferences; }
    public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
}

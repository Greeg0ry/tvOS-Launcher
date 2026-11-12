package android.text;
/** Minimal host fixture, excluded from the APK build. */
public class TextUtils {
    public static String join(CharSequence delimiter, Iterable<?> items) {
        StringBuilder text = new StringBuilder();
        for (Object item : items) { if (text.length() > 0) text.append(delimiter); text.append(item); }
        return text.toString();
    }
}

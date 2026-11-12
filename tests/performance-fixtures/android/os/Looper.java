package android.os;
/** Minimal host fixture, excluded from the APK build. */
public class Looper { public static Looper getMainLooper() { return new Looper(); } }

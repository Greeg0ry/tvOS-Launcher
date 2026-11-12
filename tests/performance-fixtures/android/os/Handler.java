package android.os;
/** Minimal host fixture, excluded from the APK build. */
public class Handler {
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { task.run(); return true; }
}

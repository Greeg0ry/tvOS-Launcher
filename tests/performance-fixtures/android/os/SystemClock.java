package android.os;
/** Monotonic host clock for the request backoff test. */
public class SystemClock {
    public static long uptimeMillis() { return System.nanoTime() / 1_000_000; }
}

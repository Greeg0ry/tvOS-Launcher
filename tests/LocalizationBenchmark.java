import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/** Host JVM comparison of dictionary initialization, not a device startup benchmark. */
public final class LocalizationBenchmark {
    public static void main(String[] args) throws Exception {
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        threads.setThreadAllocatedMemoryEnabled(true);
        long[] times = new long[15];
        long[] allocations = new long[15];
        int languages = 0;
        int entries = 0;
        URL[] urls = {Path.of(args[0]).toUri().toURL(), Path.of(args[1]).toUri().toURL()};
        for (int i = -3; i < times.length; i++) {
            try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                long beforeBytes = threads.getThreadAllocatedBytes(Thread.currentThread().getId());
                long before = System.nanoTime();
                Class<?> type = Class.forName("com.techgenius.tvoslauncher.I18n", true, loader);
                long elapsed = System.nanoTime() - before;
                long allocated = threads.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
                languages = ((String[][]) type.getField("LANGUAGES").get(null)).length;
                entries = 0;
                for (Field field : type.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Map<?, ?> map = (Map<?, ?>) field.get(null);
                    if (map == null) continue;
                    for (Object value : map.values()) {
                        if (value instanceof Map) entries += ((Map<?, ?>) value).size();
                        else entries++;
                    }
                }
                if (i >= 0) {
                    times[i] = elapsed;
                    allocations[i] = allocated;
                }
            }
        }
        Arrays.sort(times);
        Arrays.sort(allocations);
        System.out.printf(java.util.Locale.ROOT,
                "languages=%d dictionary_entries=%d init_median_ms=%.3f init_allocated_median_bytes=%d samples=%d%n",
                languages, entries, times[times.length / 2] / 1_000_000.0,
                allocations[allocations.length / 2], times.length);
    }
}

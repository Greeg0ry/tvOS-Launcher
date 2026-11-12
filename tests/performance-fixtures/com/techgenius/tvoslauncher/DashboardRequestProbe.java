package com.techgenius.tvoslauncher;
import android.content.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
/** Diagnostic probe of production scheduling, with Android and transport stand-ins. */
public class DashboardRequestProbe {
    public static void main(String[] args) throws Exception {
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, values) -> values.length == 2 ? values[1] : null);
        Context context = new Context(prefs);
        Field language = I18n.class.getDeclaredField("cachedLang");
        language.setAccessible(true); language.set(null, "ru");
        CountDownLatch callbacks = new CountDownLatch(20);
        try {
            for (int i = 0; i < 20; i++) HomeDashboard.fetchLatestReleases(context, false, result -> callbacks.countDown());
            Suggestions.started.await(3, TimeUnit.SECONDS);
            System.out.println("calls=20 force=false cache=empty identical_country_sets=" + Suggestions.signatures.size()
                    + " transport_requests=" + Suggestions.requests.get());
        } finally {
            Suggestions.release.countDown();
            callbacks.await(3, TimeUnit.SECONDS);
            Field executor = HomeDashboard.class.getDeclaredField("EXEC");
            executor.setAccessible(true); ((ExecutorService) executor.get(null)).shutdownNow();
        }
        if (Suggestions.requests.get() != 1) throw new AssertionError("Expected one in-flight fetch per signature; actual=" + Suggestions.requests.get());
        if (callbacks.getCount() != 0) throw new AssertionError("Missing callbacks: " + callbacks.getCount());
        System.out.println("PASS: one transport request, all 20 callbacks delivered");
    }
}

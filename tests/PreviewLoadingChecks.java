package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Regression probes for preview permission decisions and foreground work behind a busy IO queue. */
public class PreviewLoadingChecks extends HomeScreenChecks {
    static final String LISTINGS = "android.permission.READ_TV_LISTINGS";

    /** Unattached Activity isolates permission decisions without opening system prompts or changing user grants. */
    static final class PermissionProbe extends MainActivity {
        final SharedPreferences isolated;
        boolean granted;
        PermissionProbe(SharedPreferences isolated) { this.isolated = isolated; prefs = isolated; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return isolated; }
        @Override public int checkSelfPermission(String permission) {
            return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        }
    }

    @Override public void onStart() {
        Bundle results = new Bundle();
        List<String> failures = new ArrayList<>();
        SharedPreferences isolated = getTargetContext().getSharedPreferences("preview-permission-checks", 0);
        File fixture = new File(getTargetContext().getCacheDir(), "preview-queue-check.png");
        CountDownLatch release = new CountDownLatch(1);
        try {
            Method ensure = MainActivity.class.getDeclaredMethod("ensureTvListingsPermission");
            ensure.setAccessible(true);
            ui(() -> {
                PermissionProbe probe = new PermissionProbe(isolated);
                try {
                    isolated.edit().clear().putBoolean("setup_done", true).putBoolean("cw_enabled", false).commit();
                    ensure.invoke(probe);
                    // The production path records the request before Android opens its permission UI.
                    if (!isolated.getBoolean("asked_tv_listings_perm", false)) {
                        failures.add("Fresh setup with Continue Watching off never requests preview access");
                    }
                    checks++;
                    isolated.edit().clear().putBoolean("setup_done", true)
                            .putInt("adb_mode", LocalAdb.ADB_MODE_PERSISTENT).commit();
                    ensure.invoke(probe);
                    check(isolated.getBoolean("asked_tv_listings_perm", false),
                            "Persistent ADB must not bypass the on-screen preview permission request");
                    isolated.edit().clear().putBoolean("setup_done", false).commit();
                    ensure.invoke(probe);
                    check(!isolated.getBoolean("asked_tv_listings_perm", false), "Do not interrupt onboarding for preview access");
                    isolated.edit().putBoolean("setup_done", true).commit();
                    probe.granted = true;
                    ensure.invoke(probe);
                    check(!isolated.getBoolean("asked_tv_listings_perm", false), "Do not request access already granted");
                } catch (Exception error) { throw new AssertionError(error); }
            });
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getTargetContext(), MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> MainActivity.activeInstance != null && MainActivity.activeInstance.resumed, "Home did not resume");
            home = MainActivity.activeInstance;
            await(() -> !home.tiles.isEmpty(), "Home catalog is empty");
            Bitmap image = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888);
            image.eraseColor(0xFF42677F);
            try (FileOutputStream output = new FileOutputStream(fixture)) { image.compress(Bitmap.CompressFormat.PNG, 100, output); }
            image.recycle();
            ui(() -> { home.goTop(); home.classicPreviewLoader.cancel(); home.heroArtworkLoader.cancel(); });
            ui(() -> {
                // Model an empty provider response cached just before permission was granted.
                home.classicPreviewSignature = Profiles.current(home).id + ":" + home.shown.get(0).pkg
                        + ":" + Suggestions.version(home) + ":" + home.cwEnabled() + ":"
                        + (Suggestions.showRecs(home) && Suggestions.ready(home));
                home.heroVersion = Suggestions.version(home);
                home.classicPreviewPending = false;
                home.classicPreviewRefreshAt = SystemClock.uptimeMillis() + 60_000L;
                home.onRequestPermissionsResult(901, new String[]{LISTINGS}, new int[]{PackageManager.PERMISSION_GRANTED});
                check(home.classicPreviewPending, "Granting preview access must retry cached empty provider results immediately");
                home.classicPreviewLoader.cancel();
            });
            CountDownLatch blocked = new CountDownLatch(1);
            home.io.execute(() -> {
                blocked.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            check(blocked.await(8, TimeUnit.SECONDS), "Could not isolate background IO");
            Suggestions.Item item = new Suggestions.Item();
            item.title = "Queue regression"; item.type = "preview"; item.logo = "";
            item.backdrop = fixture.getAbsolutePath(); item.pkg = home.shown.get(0).pkg;
            CountDownLatch delivered = new CountDownLatch(1);
            ui(() -> {
                home.hideClassicCw();
                home.classicPreviewLoader.submit(() -> Collections.singletonList(item), items -> {
                    home.setHeroItems(items);
                    delivered.countDown();
                });
            });
            if (!delivered.await(3, TimeUnit.SECONDS)) {
                failures.add("Selected app data waits behind unrelated background IO");
            } else {
                try { await(() -> home.heroArtworkItem == item && home.heroShown,
                        "Selected artwork waits behind unrelated background IO"); }
                catch (AssertionError error) { failures.add(error.getMessage()); }
            }
            checks++;
            release.countDown();
            if (failures.isEmpty()) {
                java.util.List<MainActivity.AppEntry> original = new ArrayList<>(home.apps);
                try {
                    ui(() -> {
                        java.util.Map<String, MainActivity.AppEntry> utilities = new java.util.HashMap<>();
                        home.addPresentEntry(utilities);
                        home.apps.removeIf(app -> MainActivity.PRESENT_KEY.equals(app.key));
                        home.apps.add(0, utilities.get(MainActivity.PRESENT_KEY));
                        home.buildGrid();
                        home.focusIndex(0);
                    });
                    key(android.view.KeyEvent.KEYCODE_DPAD_UP);
                    ui(() -> check(!home.shelfView.isFocused(),
                            "Up from Presentation must not open the previous app's empty preview screen"));
                    key(android.view.KeyEvent.KEYCODE_DPAD_UP);
                    SystemClock.sleep(450);
                    ui(() -> check(home.scroll.getTranslationY() == 0f && !home.shelfView.isFocused(),
                            "Repeated Up on Presentation must keep the app grid on screen"));
                    key(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
                    ui(() -> check(home.tiles.get(0).isFocused(),
                            "Down from status must return to Presentation instead of an empty preview"));
                } finally {
                    ui(() -> {
                        home.apps.clear(); home.apps.addAll(original);
                        home.buildGrid(); home.focusIndex(0);
                    });
                }
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            results.putString("stream", "PASS: " + checks + " preview permission and queue checks\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable error) {
            results.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, results);
        } finally {
            if (home != null) ui(() -> { home.classicPreviewLoader.cancel(); home.heroArtworkLoader.cancel(); });
            release.countDown();
            getTargetContext().deleteSharedPreferences("preview-permission-checks");
            fixture.delete();
        }
    }
}

package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Device integration probe: real focus, scrolling and Top Shelf, using local artwork only. */
public class HomeScreenChecks extends Instrumentation {
    MainActivity home;
    int checks;
    boolean performanceOnly;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        performanceOnly = arguments != null && "performance".equals(arguments.getString("only"));
        start();
    }
    void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    void ui(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findText(((ViewGroup) view).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
    String shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
             java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
    void await(BooleanSupplier condition, String message) {
        long end = SystemClock.uptimeMillis() + 8000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < end) {
            ui(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) { checks++; return; }
            SystemClock.sleep(50);
        }
        throw new AssertionError(message);
    }
    void key(int code) {
        sendKeyDownUpSync(code);
        waitForIdleSync();
    }
    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getTargetContext(), MainActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> MainActivity.activeInstance != null, "Home did not resume");
            home = MainActivity.activeInstance;
            await(() -> home.tiles.size() > MainActivity.COLS && home.resumed, "App catalog did not load");
            if (performanceOnly) {
                ui(() -> home.focusIndex(0));
                SystemClock.sleep(600);
                shell("dumpsys gfxinfo com.techgenius.tvoslauncher reset");
                for (int i = 0; i < 24; i++) {
                    key(i % 6 < 3 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                    SystemClock.sleep(220);
                }
                StringBuilder metrics = new StringBuilder();
                for (String line : shell("dumpsys gfxinfo com.techgenius.tvoslauncher").split("\n")) {
                    if (line.matches(".*(Total frames rendered|Janky frames|50th percentile|90th percentile).*")) {
                        metrics.append(line).append('\n');
                    }
                }
                results.putString("stream", metrics.toString());
                finish(Activity.RESULT_OK, results);
                return;
            }
            android.content.SharedPreferences catalogPrefs = getTargetContext().getSharedPreferences("home-checks-catalog", 0);
            String legacyOther = home.apps.get(0).key;
            String manuallyHidden = home.apps.get(1).key;
            try {
                catalogPrefs.edit().putStringSet(MainActivity.PREF_OTHER, Collections.singleton(legacyOther))
                        .putStringSet(MainActivity.PREF_HIDDEN, Collections.singleton(manuallyHidden)).commit();
                java.lang.reflect.Method readCatalog = MainActivity.class.getDeclaredMethod("readAppCatalog", android.content.SharedPreferences.class);
                readCatalog.setAccessible(true);
                Object catalog = readCatalog.invoke(home, catalogPrefs);
                java.lang.reflect.Field appsField = catalog.getClass().getDeclaredField("apps");
                appsField.setAccessible(true);
                java.util.List<?> visible = (java.util.List<?>) appsField.get(catalog);
                boolean includesLegacyOther = false, includesHidden = false;
                for (Object entry : visible) {
                    String key = ((MainActivity.AppEntry) entry).key;
                    includesLegacyOther |= legacyOther.equals(key);
                    includesHidden |= manuallyHidden.equals(key);
                }
                check(includesLegacyOther, "Legacy Other Apps entries must appear in the ordinary app list");
                check(!includesHidden, "Manually hidden apps must remain hidden");
            } finally { getTargetContext().deleteSharedPreferences("home-checks-catalog"); }
            ui(() -> {
                check(home.shown.size() == home.apps.size()
                        && home.shown.stream().noneMatch(entry -> MainActivity.FOLDER_KEY.equals(entry.key)),
                        "Home should show every visible app without an Other Apps folder");
                home.showOptions(0);
                check(findText(home.root, I18n.t(home, "Move to Other Apps")) == null,
                        "App options must not offer the removed Other Apps folder");
                home.closeMenu();
            });
            ui(() -> {
                WeatherView weather = new WeatherView(home, false, home.unit);
                Weather.Data longPlace = new Weather.Data();
                longPlace.place = "Petropavlovsk-Kamchatsky City Centre";
                longPlace.temp = -18; longPlace.max = 2; longPlace.min = -22; longPlace.code = 95;
                weather.setData(longPlace);
                int width = home.dp(WeatherView.COMPACT_W), height = home.dp(WeatherView.COMPACT_H);
                weather.layout(0, 0, width, height);
                Bitmap render = Bitmap.createBitmap(width + home.dp(240), height, Bitmap.Config.ARGB_8888);
                weather.draw(new android.graphics.Canvas(render));
                int overflow = 0;
                for (int y = 0; y < height; y++) for (int x = width + 4; x < render.getWidth(); x++) {
                    if (android.graphics.Color.alpha(render.getPixel(x, y)) > 120) overflow++;
                }
                check(overflow == 0, "Compact weather draws text beyond its bounds: " + overflow + " pixels");
                longPlace.max = 42; longPlace.min = -99;
                Bitmap changed = Bitmap.createBitmap(render.getWidth(), height, Bitmap.Config.ARGB_8888);
                weather.draw(new android.graphics.Canvas(changed));
                check(render.sameAs(changed), "Forecast extremes must not appear in the compact pill");
                render.recycle(); changed.recycle();
            });
            ui(() -> RecentAppsOverlay.showInActivity(home));
            await(RecentAppsOverlay::isShowing, "Memory / Recent Apps did not open");
            key(KeyEvent.KEYCODE_BACK);
            await(() -> !RecentAppsOverlay.isShowing(), "Back did not close Memory / Recent Apps inside Home");
            ui(() -> {
                home.showControlCenter();
                View memory = findText(home.root, I18n.t(home, "Memory"));
                check(memory != null, "Memory tile missing from Control Center");
                while (!memory.isClickable() && memory.getParent() instanceof View) memory = (View) memory.getParent();
                check(memory.performClick(), "Could not select Memory tile");
            });
            await(RecentAppsOverlay::isShowing, "Memory menu did not open Recents");
            key(KeyEvent.KEYCODE_BACK);
            await(() -> !RecentAppsOverlay.isShowing(), "Back from actual Control Center Memory tile failed");
            ui(() -> {
                check(!Scenes.isScenesLook(home), "Classic must be selected for this probe");
                check(MainActivity.COLS == 5, "Expected five columns");
                home.goTop();
                check(home.tiles.get(0).isFocused(), "Home should select the first app");
            });
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            ui(() -> check(home.focusedIndex == 1, "Right should select second column"));
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            await(() -> home.focusedIndex == 6 && home.scroll.getScrollY() > 0,
                    "Down should preserve column and scroll to lower row");
            ui(() -> check(home.classicDockLabel.getAlpha() == 0f,
                    "Dock caption must hide when a lower row is focused"));
            key(KeyEvent.KEYCODE_DPAD_UP);
            await(() -> home.focusedIndex == 1 && home.scroll.getScrollY() == 0,
                    "Up should restore the same dock column");
            ui(() -> {
                home.focusIndex(home.tiles.size() - 1);
                home.goTop();
                check(home.focusedIndex == 0 && home.tiles.get(0).isFocused(), "Home from last row failed");
            });
            File image = new File(getTargetContext().getCacheDir(), "home-check-art.jpg");
            Bitmap fixture = BitmapFactory.decodeStream(getTargetContext().getAssets().open("backgrounds/scene-dawn.jpg"));
            try (FileOutputStream out = new FileOutputStream(image)) { fixture.compress(Bitmap.CompressFormat.JPEG, 85, out); }
            fixture.recycle();
            Suggestions.Item item = new Suggestions.Item();
            item.title = "Local preview check"; item.overview = "Local integration fixture";
            item.type = "preview"; item.year = "2026"; item.genres = ""; item.logo = "";
            item.pkg = home.shown.get(0).pkg; item.backdrop = image.getPath();
            ui(() -> {
                home.classicPreviewLoader.cancel();
                home.hideClassicCw();
                home.setHeroItems(Collections.singletonList(item));
            });
            await(() -> home.heroFront.getDrawable() != null && home.heroShown
                    && home.heroInfo.getVisibility() == View.VISIBLE, "Top Shelf artwork did not appear");
            java.util.concurrent.CountDownLatch releasePreviewWorker = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch previewWorkerBlocked = new java.util.concurrent.CountDownLatch(1);
            home.previewIo.execute(() -> {
                previewWorkerBlocked.countDown();
                try { releasePreviewWorker.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            check(previewWorkerBlocked.await(8, java.util.concurrent.TimeUnit.SECONDS), "Could not isolate preview worker");
            android.graphics.drawable.Drawable readyPreview = home.heroFront.getDrawable();
            try {
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                key(KeyEvent.KEYCODE_DPAD_UP);
                SystemClock.sleep(450); // Allow the grid transition to finish while the worker remains blocked.
                ui(() -> {
                    check(home.tiles.get(0).isFocused(), "Returning from the lower row must restore the first app");
                    check(home.heroShown && home.heroArtworkItem == item && home.heroFront.getDrawable() == readyPreview,
                            "Returning to the dock must restore its ready preview without waiting for the worker");
                });
                ui(() -> {
                    home.classicPreviewRefreshAt = 0;
                    home.loadClassicPreview();
                    check(home.heroShown && home.heroArtworkItem == item,
                            "Refreshing an expired preview must keep its current artwork visible");
                });
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                key(KeyEvent.KEYCODE_DPAD_UP);
                SystemClock.sleep(450);
                ui(() -> check(home.heroShown && home.heroArtworkItem == item && home.heroFront.getDrawable() == readyPreview,
                        "An interrupted background refresh must not delay return to the prepared preview"));
            } finally {
                ui(() -> home.classicPreviewLoader.cancel());
                releasePreviewWorker.countDown();
            }
            Suggestions.Item missing = new Suggestions.Item();
            missing.title = "Unavailable artwork"; missing.overview = ""; missing.type = "movie";
            missing.year = ""; missing.genres = ""; missing.logo = ""; missing.pkg = item.pkg;
            missing.backdrop = new File(getTargetContext().getCacheDir(), "does-not-exist.jpg").getPath();
            ui(() -> home.setHeroItems(Collections.singletonList(missing)));
            await(() -> home.heroArtworkItem == null && !home.heroShown && !home.shelfView.isFocusable(),
                    "Failed artwork must fall back to the neutral background without previous app artwork");
            ui(() -> home.setHeroItems(Collections.singletonList(item)));
            await(() -> home.heroArtworkItem == item && home.heroShown, "Preview did not recover after missing artwork");
            key(KeyEvent.KEYCODE_DPAD_UP);
            await(() -> home.shelfView.isFocused() && home.scroll.getTranslationY() > 0,
                    "Up should open fullscreen Top Shelf");
            ui(() -> check(home.classicDockLabel.getAlpha() == 0f,
                    "Dock caption must hide in fullscreen preview"));
            ui(() -> {
                check(!home.heroIsContinue(), "Published previews must not be treated as playback history");
                check(home.heroRemove.getVisibility() == View.GONE, "Published preview must not show Remove history");
                check(home.heroBtnText.getText().toString().contains(I18n.t(home, "Open")), "Published preview should offer Open");
            });
            key(KeyEvent.KEYCODE_BACK);
            await(() -> home.tiles.get(0).isFocused() && home.scroll.getTranslationY() == 0,
                    "Back should restore the dock and original focused app");
            ui(() -> {
                home.classicPreviewLoader.cancel();
                home.setHeroItems(Collections.emptyList());
                home.focusIndex(1);
                home.loadClassicPreview();
                check(!home.heroShown, "Another app must not inherit the previous app preview");
                home.showSearch(null);
            });
            key(KeyEvent.KEYCODE_HOME);
            await(() -> MainActivity.activeInstance != null && MainActivity.activeInstance.search == null
                    && MainActivity.activeInstance.tiles.get(0).isFocused(), "Home should close Search and select first app");
            home = MainActivity.activeInstance;
            ui(() -> home.enterLockScreen(false));
            await(() -> home.lockScreenActive && home.sceneHome.sceneView.getVisibility() == View.VISIBLE,
                    "Built-in screensaver must survive removal of Home wallpapers");
            await(() -> home.sceneHome.sceneView.getCurrentBgBitmap() != null
                    && home.sceneHome.sceneView.getCurrentBgBitmap().getWidth() > 64,
                    "Screensaver must load a real scene after Home wallpaper assets are removed");
            ui(() -> check(home.classicDockLabel.getVisibility() == View.GONE, "Screensaver must hide dock caption"));
            SystemClock.sleep(700);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            await(() -> !home.lockScreenActive && home.scroll.getVisibility() == View.VISIBLE,
                    "Any key should return from the screensaver to Home");
            android.app.Activity settings = startActivitySync(new Intent(home, SettingsActivity.class)
                    .putExtra("page", SettingsActivity.LOOK_SCENES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(() -> {
                check(findText(settings.getWindow().getDecorView(), I18n.t(home, "Custom wallpaper mode")) == null,
                        "Home wallpaper picker must be removed from Settings");
                check(findText(settings.getWindow().getDecorView(), I18n.t(home, "Lock screen settings\u2026")) != null,
                        "Screensaver settings must remain accessible");
                settings.finish();
            });
            SetupActivity setup = (SetupActivity) startActivitySync(new Intent(home, SetupActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(() -> {
                check(findText(setup.getWindow().getDecorView(), I18n.t(home, "Get Started")) != null,
                        "One-screen onboarding needs a single start action");
                check(findText(setup.getWindow().getDecorView(), I18n.t(home, "Choose a Background")) == null,
                        "Onboarding must not ask for wallpapers");
                setup.openHome();
            });
            await(() -> SetupActivity.done(home) && MainActivity.activeInstance != null
                    && MainActivity.activeInstance.resumed, "Start should finish onboarding immediately");
            results.putString("stream", "PASS: " + checks + " real Home integration checks at "
                    + home.screenW + "x" + home.screenH + "\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable error) {
            results.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, results);
        }
    }
}

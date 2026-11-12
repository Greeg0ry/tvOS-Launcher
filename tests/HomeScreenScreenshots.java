package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

/** Capture real device screens without fixtures or changes to the user's saved settings. */
public final class HomeScreenScreenshots extends HomeScreenChecks {
    File directory;
    int captured;

    void capture(String name) throws Exception {
        waitForIdleSync();
        SystemClock.sleep(650);
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        if (screenshot == null) throw new AssertionError("No screenshot for " + name);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { screenshot.recycle(); }
        captured++;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getTargetContext(), MainActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> MainActivity.activeInstance != null, "Home did not resume");
            home = MainActivity.activeInstance;
            await(() -> home.tiles.size() > MainActivity.COLS && home.resumed, "Apps did not load");
            directory = getTargetContext().getExternalFilesDir("screenshots");
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
                throw new AssertionError("Screenshot directory unavailable");
            }
            ui(() -> home.focusIndex(Math.min(1, home.tiles.size() - 1)));
            SystemClock.sleep(1800);
            capture("home");
            if (home.heroShown) {
                key(KeyEvent.KEYCODE_DPAD_UP);
                capture("top-shelf");
                key(KeyEvent.KEYCODE_BACK);
            }
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            capture("apps");
            ui(() -> { home.focusIndex(0); home.tiles.get(0).performLongClick(); });
            capture("app-menu");
            key(KeyEvent.KEYCODE_BACK);
            ui(() -> home.showSearch(null));
            capture("search");
            key(KeyEvent.KEYCODE_BACK);
            ui(() -> home.showWeather());
            capture("weather");
            key(KeyEvent.KEYCODE_BACK);
            ui(() -> home.showControlCenter());
            capture("control-center");
            ui(() -> {
                View memory = findText(home.root, I18n.t(home, "Memory"));
                if (memory == null) throw new AssertionError("Memory tile unavailable");
                while (!memory.isClickable() && memory.getParent() instanceof View) memory = (View) memory.getParent();
                memory.performClick();
            });
            capture("memory");
            key(KeyEvent.KEYCODE_BACK);
            ui(() -> home.showProfilePicker(false));
            capture("profiles");
            key(KeyEvent.KEYCODE_BACK);
            ui(() -> home.showInputs(home.ccButton));
            capture("inputs");
            key(KeyEvent.KEYCODE_BACK);
            String[][] sections = {
                {SettingsActivity.LOOK_SCENES, "settings"},
                {SettingsActivity.APPS, "settings-apps"},
                {SettingsActivity.WEATHER, "settings-weather"},
                {SettingsActivity.LANGUAGE, "settings-language"},
                {SettingsActivity.CONTROL_PANEL, "settings-controls"},
                {SettingsActivity.ADVANCED, "settings-advanced"},
                {SettingsActivity.ABOUT, "settings-about"}
            };
            for (String[] section : sections) {
                ui(() -> home.startActivity(new Intent(home, SettingsActivity.class).putExtra("page", section[0])));
                capture(section[1]);
            }
            ui(() -> home.startActivity(new Intent(home, SetupActivity.class)));
            capture("setup");
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getTargetContext(), MainActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> MainActivity.activeInstance != null && MainActivity.activeInstance.resumed, "Home did not return");
            home = MainActivity.activeInstance;
            ui(() -> home.enterLockScreen(false));
            SystemClock.sleep(1800);
            capture("screensaver");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            result.putString("stream", "PASS: captured " + captured + " current device screens\nSCREENSHOTS_DIR=" + directory.getAbsolutePath() + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}

package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Real remote navigation, direct launcher settings and the nested device settings action. */
public final class HomeNavigationChecks extends HomeScreenChecks {
    View findDescription(View view, String text) {
        if (view == null) return null;
        if (view.getContentDescription() != null && text.contentEquals(view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        List<String> failures = new ArrayList<>();
        Activity settings = null;
        try {
            getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getTargetContext(), MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> MainActivity.activeInstance != null && MainActivity.activeInstance.resumed, "Home did not resume");
            home = MainActivity.activeInstance;
            await(() -> !home.tiles.isEmpty(), "App catalog did not load");
            ui(home::goTop);
            key(KeyEvent.KEYCODE_DPAD_UP);
            key(KeyEvent.KEYCODE_DPAD_UP);
            ui(() -> {
                for (String title : new String[]{"dash_prayer_times", "dash_football", "dash_latest_releases"}) {
                    View card = findText(home.root, I18n.t(home, title).toUpperCase(I18n.locale(home)));
                    if (card != null && card.isShown()) failures.add("Double Up exposes removed widget: " + title);
                    checks++;
                }
                check(home.getCurrentFocus() != null, "Repeated Up must retain focus");
            });
            try {
                Class.forName("com.techgenius.tvoslauncher.HomeDashboard");
                failures.add("Dashboard widgets and their data loaders remain packaged in the application");
            } catch (ClassNotFoundException expected) { checks++; }
            key(KeyEvent.KEYCODE_HOME);
            await(() -> home.tiles.get(0).isFocused(), "Home must return to first app after repeated Up");
            ActivityMonitor launcherSettings = addMonitor(SettingsActivity.class.getName(), null, false);
            try {
                ui(() -> {
                    int index = -1;
                    for (int i = 0; i < home.shown.size(); i++) {
                        MainActivity.AppEntry app = home.shown.get(i);
                        if (MainActivity.SETTINGS_KEY.equals(app.key) || MainActivity.TV_SETTINGS.equals(app.pkg)) { index = i; break; }
                    }
                    check(index >= 0, "Settings app entry is missing");
                    home.focusIndex(index);
                    home.tiles.get(index).performClick();
                });
                settings = waitForMonitorWithTimeout(launcherSettings, 1800);
                if (settings == null) {
                    failures.add("Settings tile opens a choice popup instead of launcher settings");
                    ui(() -> home.sceneHome.closeSheetImmediate());
                    settings = startActivitySync(new Intent(home, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else checks++;
            } finally { removeMonitor(launcherSettings); }
            final Activity screen = settings;
            ui(() -> {
                if (findText(screen.getWindow().getDecorView(), I18n.t(screen, "Home screen widget settings")) != null) {
                    failures.add("Removed widget settings are still offered");
                } else checks++;
            });
            View device = findText(screen.getWindow().getDecorView(), I18n.t(screen, "Device settings"));
            if (device == null) failures.add("Device settings is missing inside launcher settings");
            else {
                ActivityMonitor systemSettings = addMonitor(new IntentFilter(android.provider.Settings.ACTION_SETTINGS),
                        new ActivityResult(Activity.RESULT_CANCELED, null), true);
                try {
                    final View label = device;
                    ui(() -> {
                        View row = label;
                        while (!row.isClickable() && row.getParent() instanceof View) row = (View) row.getParent();
                        check(row.performClick(), "Device settings row cannot be selected");
                    });
                    check(systemSettings.getHits() == 1, "Device settings must open Android settings");
                } finally { removeMonitor(systemSettings); }
            }
            ui(screen::finish);
            settings = null;
            await(() -> home.resumed, "Home did not resume after settings");
            ActivityMonitor controlSettings = addMonitor(SettingsActivity.class.getName(), null, false);
            try {
                ui(() -> {
                    home.showControlCenter();
                    View label = findDescription(home.pickerLayer,
                            ControlPanelOverlay.getItemTitle(home, ControlPanelOverlay.ITEM_SETTINGS));
                    check(label != null, "Control Center settings button is missing");
                    while (!label.isClickable() && label.getParent() instanceof View) label = (View) label.getParent();
                    check(label.performClick(), "Control Center settings button cannot be selected");
                });
                settings = waitForMonitorWithTimeout(controlSettings, 1800);
                check(settings != null, "Control Center must open launcher settings directly");
            } finally { removeMonitor(controlSettings); }
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            result.putString("stream", "PASS: " + checks + " Home navigation and settings checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (settings != null) { final Activity screen = settings; ui(screen::finish); }
        }
    }
}

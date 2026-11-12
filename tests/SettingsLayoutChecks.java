package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Measures actual Russian/English settings labels; no row actions or preference changes. */
public final class SettingsLayoutChecks extends HomeScreenChecks {
    int pages;
    int labels;
    final List<String> clipped = new ArrayList<>();
    final java.util.Set<View> headings = new java.util.HashSet<>();

    boolean isLabel(TextView text) {
        if (!(text.getParent() instanceof ViewGroup)) return false;
        ViewGroup parent = (ViewGroup) text.getParent();
        if (parent.isClickable()) {
            return parent.getChildAt(0) == text || (parent.getChildCount() == 2
                    && parent.getChildAt(0) instanceof android.widget.FrameLayout && parent.getChildAt(1) == text);
        }
        return parent.getChildAt(0) == text && parent.getParent() instanceof View
                && ((View) parent.getParent()).isClickable();
    }

    void inspect(View view, String page) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if ((!isLabel(text) && !headings.contains(text)) || text.getText().length() == 0) return;
            labels++;
            Layout layout = text.getLayout();
            int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
            boolean bad = layout == null || width <= 0;
            if (layout != null) {
                for (int line = 0; line < layout.getLineCount(); line++) {
                    bad |= layout.getEllipsisCount(line) > 0 || layout.getLineMax(line) > width + 2;
                }
                bad |= layout.getHeight() > text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 2;
            }
            if (bad) clipped.add(page + ": " + text.getText() + " [width=" + width + ", lines="
                    + (layout == null ? 0 : layout.getLineCount()) + ", height=" + text.getHeight() + "]");
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) inspect(group.getChildAt(i), page);
        }
    }

    void inspectPage(SettingsActivity screen, String name) {
        waitForIdleSync();
        SystemClock.sleep(90);
        ui(() -> inspect(screen.getWindow().getDecorView(), name));
        pages++;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SettingsActivity screen = null;
        Field language = null;
        Object originalLanguage = null;
        try {
            language = I18n.class.getDeclaredField("cachedLang"); language.setAccessible(true);
            originalLanguage = language.get(null);
            Field sidebar = SettingsActivity.class.getDeclaredField("SIDEBAR_SECTIONS"); sidebar.setAccessible(true);
            String[][] sections = (String[][]) sidebar.get(null);
            Method select = SettingsActivity.class.getDeclaredMethod("selectSection", String.class, boolean.class, int.class);
            select.setAccessible(true);
            Method submenu = SettingsActivity.class.getDeclaredMethod("openSubMenu", String.class); submenu.setAccessible(true);
            for (String locale : new String[]{"ru", "en"}) {
                language.set(null, locale); // In-memory language override; saved preferences are untouched.
                screen = (SettingsActivity) startActivitySync(new Intent(getTargetContext(), SettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                final SettingsActivity settings = screen;
                headings.clear();
                for (String name : new String[]{"mainHeadView", "sideTitleView"}) {
                    Field heading = SettingsActivity.class.getDeclaredField(name); heading.setAccessible(true);
                    headings.add((View) heading.get(settings));
                }
                for (String[] section : sections) {
                    ui(() -> { try { select.invoke(settings, section[0], false, 0); } catch (Exception e) { throw new AssertionError(e); } });
                    inspectPage(settings, locale + "/" + section[0]);
                    if (SettingsActivity.ABOUT.equals(section[0])) {
                        ui(() -> {
                            if (findText(settings.getWindow().getDecorView(), "Greeg0ry") == null) {
                                clipped.add(locale + "/About: fork maintainer is missing");
                            }
                        });
                    }
                }
                String[] menus = {"dock_size", "lock_screen_settings", "weather_source", "top_shelf", "default_home",
                        "check_permissions", "cyt_models", "cyt_models_all", "cyt_models_scan", "cyt_debloat",
                        "cyt_launchers", "cyt_manual", "cyt_saved", "cyt_restore",
                        "profile_actions:" + Profiles.current(settings).id, "bm_key:3", "bm_tap:3:1", "cp_app:settings"};
                for (String menu : menus) {
                    ui(() -> { try { submenu.invoke(settings, menu); } catch (Exception e) { throw new AssertionError(e); } });
                    inspectPage(settings, locale + "/" + menu);
                }
                ui(settings::finish);
                screen = null;
            }
            if (!clipped.isEmpty()) throw new AssertionError(clipped.size() + " clipped settings labels:\n"
                    + String.join("\n", clipped.subList(0, Math.min(40, clipped.size()))));
            check(labels > 100, "Settings width audit must inspect actual menu labels");
            result.putString("stream", "PASS: " + labels + " settings labels across " + pages + " Russian/English pages\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            try { if (language != null) language.set(null, originalLanguage); } catch (Exception ignored) { }
            if (screen != null) { final Activity current = screen; ui(current::finish); }
        }
    }
}

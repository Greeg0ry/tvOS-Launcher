package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One-screen onboarding: language and Home; permissions are requested by each feature. */
public class SetupActivity extends Activity {
    static final String K_DONE = "setup_done";

    static boolean done(Activity activity) {
        return activity.getSharedPreferences("setup", MODE_PRIVATE).getBoolean(K_DONE, false);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Clicks.init(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        String language = getIntent().getStringExtra("lang");
        if (language != null && I18n.isSupportedLang(language)) I18n.setLang(this, language);
        if (getIntent().getBooleanExtra("skip", false)) {
            markDone();
            finish();
            return;
        }
        buildScreen();
    }

    void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MainActivity.HOME_BACKGROUND);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(u(12), 0, u(12), 0);
        root.addView(content, new FrameLayout.LayoutParams(u(440), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        TextView title = label("tvOS Launcher", 38, "display", Color.WHITE);
        title.setLetterSpacing(-0.025f);
        content.addView(title);
        TextView introduction = label(I18n.t(this, "Choose a language and start."), 15, "regular", 0xB3FFFFFF);
        LinearLayout.LayoutParams introductionLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introductionLayout.topMargin = u(12);
        introductionLayout.bottomMargin = u(36);
        content.addView(introduction, introductionLayout);
        LinearLayout languages = new LinearLayout(this);
        languages.setClipChildren(false);
        String selectedLanguage = I18n.getLang(this);
        for (String[] language : I18n.LANGUAGES) {
            TextView choice = button(language[1] + (language[0].equals(selectedLanguage) ? "  \u2713" : ""), () -> {
                I18n.setLang(this, language[0]);
                buildScreen();
            });
            LinearLayout.LayoutParams languageLayout = new LinearLayout.LayoutParams(0, u(48), 1f);
            if (languages.getChildCount() > 0) languageLayout.leftMargin = u(12);
            languages.addView(choice, languageLayout);
        }
        content.addView(languages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(48)));
        TextView start = button(I18n.t(this, "Get Started"), this::openHome);
        LinearLayout.LayoutParams startLayout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u(52));
        startLayout.topMargin = u(24);
        content.addView(start, startLayout);
        TextView note = label(I18n.t(this, "You can change language and other options in Settings."),
                12, "regular", 0x80FFFFFF);
        note.setGravity(Gravity.CENTER);
        note.setMaxLines(2);
        LinearLayout.LayoutParams noteLayout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLayout.topMargin = u(20);
        content.addView(note, noteLayout);
        setContentView(root);
        start.requestFocus();
    }

    TextView label(String text, float size, String weight, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(color);
        label.setTypeface(Ui.font(this, weight));
        label.setIncludeFontPadding(false);
        Ui.textSize(label, size);
        return label;
    }

    TextView button(String text, Runnable action) {
        TextView button = label(text, 16, "medium", Color.WHITE);
        button.setId(View.generateViewId());
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setClickable(true);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(0xFF29292E);
        surface.setCornerRadius(u(12));
        button.setBackground(surface);
        button.setOnFocusChangeListener((view, focused) -> {
            surface.setColor(focused ? Color.WHITE : 0xFF29292E);
            button.setTextColor(focused ? 0xFF111114 : Color.WHITE);
            view.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                    .setDuration(180).setInterpolator(MainActivity.EASE).start();
        });
        button.setOnClickListener(view -> action.run());
        return button;
    }

    void markDone() {
        getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean(K_DONE, true).remove("cur_step").apply();
    }

    void openHome() {
        markDone();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    int u(float value) { return Ui.px(this, value); }
}

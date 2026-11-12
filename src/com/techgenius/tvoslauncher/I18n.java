package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** English/Russian localization, including symbolic keys, dates and whole-message templates. */
public final class I18n {
    public static final String LANG_EN = "en";
    public static final String LANG_RU = "ru";
    public static final String[][] LANGUAGES = {
        {LANG_RU, "Русский", "Использовать tvOS Launcher на русском"},
        {LANG_EN, "English", "Use tvOS Launcher in English"}
    };
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");
    private static final Map<String, String> EN_MAP = new HashMap<>();
    private static final Map<String, String> RU_MAP = new HashMap<>();
    private static String cachedLang;

    static {



        EN_MAP.put("weather", "Weather");












        EN_MAP.put("weather_updating", "Updating\u2026");
        EN_MAP.put("tomorrow_short", "Tomorrow");
        EN_MAP.put("tomorrow", "Tomorrow");
        EN_MAP.put("now", "Now");
        EN_MAP.put("bm_inputs_overlay", "TV Inputs Panel (Overlay)");
        EN_MAP.put("caffeine_title", "Caffeine (Prevent Sleep & Ambient Mode)");
        EN_MAP.put("caffeine_desc", "Caffeine denies Android's idle mode and prevents Android TV's automatic Ambient Mode from turning on, keeping your TV awake and preventing it from going to sleep.");
        EN_MAP.put("cp_bg_mode", "Control Panel background");
        EN_MAP.put("cp_bg_blurry", "Blurry (Current content blur)");
        EN_MAP.put("cp_bg_transparent", "Transparent");
        EN_MAP.put("next_wallpaper", "Next Wallpaper");
        EN_MAP.put("align_dynamic", "Dynamic");
        EN_MAP.put("align_left", "Left");
        EN_MAP.put("align_center", "Center");
        EN_MAP.put("align_right", "Right");
        EN_MAP.put("settings", "Settings");
        EN_MAP.put("home_settings", "Home Settings");
        EN_MAP.put("tv_settings", "TV Settings");
        EN_MAP.put("sleep_timer", "Sleep Timer");
        EN_MAP.put("keep_watching", "Keep watching");


        RussianTranslations.addTo(RU_MAP);
    }

    private I18n() { }

    public static boolean isSupportedLang(String lang) {
        return LANG_EN.equals(lang) || LANG_RU.equals(lang);
    }

    public static String detectSystemLang(Context c) {
        String raw = null;
        try {
            if (c != null) {
                android.os.LocaleList locales = c.getResources().getConfiguration().getLocales();
                if (locales != null && !locales.isEmpty()) raw = locales.get(0).getLanguage();
            }
        } catch (Exception ignored) { }
        if (raw == null || raw.isEmpty()) raw = Locale.getDefault().getLanguage();
        return LANG_RU.equals(raw) ? LANG_RU : LANG_EN;
    }

    public static void resetCache() {
        cachedLang = null;
    }

    public static String getLang(Context c) {
        if (cachedLang != null) return cachedLang;
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String saved = p.getString("lang", null);
        if (!isSupportedLang(saved)) {
            saved = detectSystemLang(c);
            p.edit().putString("lang", saved).apply();
            if (!p.contains("clock_24h")) Scenes.setClock24h(c, isDefault24hForLang(saved));
        }
        cachedLang = saved;
        return saved;
    }

    public static boolean isDefault24hForLang(String lang) {
        return LANG_RU.equals(lang);
    }

    public static void setLang(Context c, String lang) {
        String valid = isSupportedLang(lang) ? lang : LANG_EN;
        cachedLang = valid;
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("lang", valid).apply();
        Scenes.setClock24h(c, isDefault24hForLang(valid));
    }

    public static Locale locale(Context c) {
        return LANG_RU.equals(getLang(c)) ? Locale.forLanguageTag("ru-RU") : Locale.US;
    }

    /** Full English messages are also keys; symbolic aliases resolve before the Russian lookup. */
    public static String t(Context c, String key) {
        if (key == null) return "";
        String english = EN_MAP.getOrDefault(key, key);
        if (!LANG_RU.equals(getLang(c))) return english;
        String translated = RU_MAP.get(key);
        if (translated == null) translated = RU_MAP.get(english);
        if (translated != null) return translated;
        if (english.endsWith(" min left")) {
            return "Осталось " + english.substring(0, english.length() - " min left".length()) + " мин.";
        }
        return english;
    }

    /** Substitute once, preserving braces and dollar signs inside user-supplied values. */
    public static String format(Context c, String key, Object... values) {
        Matcher matcher = PLACEHOLDER.matcher(t(c, key));
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String value = index < values.length ? String.valueOf(values[index]) : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String formatSceneDate(Context c, Date date) {
        Locale loc = LANG_RU.equals(getLang(c)) ? locale(c) : Locale.UK;
        return new SimpleDateFormat("EEEE d MMMM", loc).format(date);
    }

    /** Translate generated names only; keep names entered by the user. */
    public static String profileName(Context c, String name) {
        if (name == null) return "";
        if (!LANG_RU.equals(getLang(c))) return name;
        if (name.equals("Default")) return "Основной";
        if (name.equals("Kids")) return "Детский";
        for (String prefix : new String[]{"Person ", "Profile "}) {
            if (name.startsWith(prefix)) {
                String suffix = name.substring(prefix.length());
                if (suffix.length() == 1 && suffix.charAt(0) >= '1' && suffix.charAt(0) <= '3') {
                    return "Профиль " + suffix;
                }
            }
        }
        return name;
    }
}

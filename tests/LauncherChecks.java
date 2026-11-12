package com.techgenius.tvoslauncher;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;

/** Host-side checks of real localization and ADB-result code; no Android UI simulation. */
public final class LauncherChecks {
    private static int assertions;

    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("Expected: " + expected + "; got: " + actual);
    }

    public static void main(String[] args) throws Exception {
        Field lang = I18n.class.getDeclaredField("cachedLang");
        lang.setAccessible(true);
        equal(2, I18n.LANGUAGES.length);
        equal(Set.of("en", "ru"), java.util.Arrays.stream(I18n.LANGUAGES)
                .map(row -> row[0]).collect(java.util.stream.Collectors.toSet()));
        lang.set(null, "ru");
        equal("Слева", I18n.t(null, "Left"));
        equal("Слева", I18n.t(null, "align_left"));
        equal("Заставка", I18n.t(null, "Ambient Mode"));
        equal("Погода", I18n.t(null, "weather"));
        equal("Профиль 1", I18n.profileName(null, "Person 1"));
        equal("Детский", I18n.profileName(null, "Kids"));
        equal("Person Smith", I18n.profileName(null, "Person Smith"));
        equal("Мой профиль", I18n.profileName(null, "Мой профиль"));
        equal("Осталось 21 мин.", I18n.t(null, "21 min left"));
        equal("Размер панели: 125%.", I18n.format(null, "Dock size set to {0}%.", 125));
        equal("Open $1\\{0} {1}", I18n.format(null, "Open {0} {1}", "$1\\{0}"));
        equal("Сериал: {1}", I18n.format(null, "{0}: {1}", "Сериал"));
        equal("Включено: 2 / найдено: 5 ›", I18n.format(null, "{0} active / {1} found ›", 2, 5));

        Set<String> unchanged = Set.of("", "Wi-Fi", "Bluetooth", "iPhone Duo", "iPhone Rounded");
        int checkedKeys = 0;
        for (String encoded : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String key = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            String translated = I18n.t(null, key);
            if (key.matches(".*[A-Za-z].*") && !unchanged.contains(key)) {
                if (translated.equals(key)) throw new AssertionError("Missing Russian translation: " + key);
                if (!translated.matches("(?s).*[А-Яа-яЁё].*")) throw new AssertionError("No Russian text: " + key);
            }
            // Translations must preserve every dynamic value, even if reordered.
            java.util.regex.Matcher markers = java.util.regex.Pattern.compile("\\{\\d+\\}").matcher(key);
            while (markers.find()) {
                if (!translated.contains(markers.group())) throw new AssertionError("Lost placeholder: " + key);
            }
            checkedKeys++;
        }

        lang.set(null, "en");
        equal("Version 2.1", I18n.format(null, "Version {0}", "2.1"));
        equal("1 / 2", I18n.format(null, "{0} / {1}", 1, 2));
        equal(true, I18n.isSupportedLang("ru"));
        equal(true, I18n.isSupportedLang("en"));
        equal(false, I18n.isSupportedLang("tr"));
        equal(false, I18n.isSupportedLang(null));
        equal(true, I18n.isDefault24hForLang("ru"));
        equal(false, I18n.isDefault24hForLang("en"));
        equal(SearchPanel.RUSSIAN, SearchPanel.keyboardCharacters(true, 0));
        equal(SearchPanel.LATIN, SearchPanel.keyboardCharacters(true, 2));
        equal(SearchPanel.LATIN, SearchPanel.keyboardCharacters(false, 0));
        equal(SearchPanel.RUSSIAN, SearchPanel.keyboardCharacters(false, 2));
        equal(SearchPanel.DIGITS, SearchPanel.keyboardCharacters(true, 1));
        equal("abc", SearchPanel.keyboardToggleLabel(true, 0));
        equal("абв", SearchPanel.keyboardToggleLabel(true, 2));
        equal("абв", SearchPanel.keyboardToggleLabel(false, 0));

        String pkg = "com.example.tv";
        equal(true, PackageCommandResult.succeeded("Package " + pkg + " new state: disabled-user\n", pkg, true));
        equal(true, PackageCommandResult.succeeded("  Package " + pkg + " new state: enabled\r\n", pkg, false));
        equal(false, PackageCommandResult.succeeded("Error: SecurityException", pkg, true));
        equal(false, PackageCommandResult.succeeded("Package " + pkg + " new state: enabled", pkg, true));
        equal(false, PackageCommandResult.succeeded("Package com.other new state: disabled-user", pkg, true));
        equal(false, PackageCommandResult.succeeded("", pkg, false));
        equal(false, PackageCommandResult.succeeded(null, pkg, true));
        System.out.println("PASS: " + checkedKeys + " localization keys; " + assertions + " behavior assertions");
    }
}

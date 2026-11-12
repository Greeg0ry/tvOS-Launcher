package com.techgenius.tvoslauncher;

/** Confirms PackageManager's resulting state rather than mere ADB connectivity. */
final class PackageCommandResult {
    private PackageCommandResult() { }

    static boolean succeeded(String output, String pkg, boolean disabled) {
        if (output == null || pkg == null || pkg.isEmpty()) return false;
        String expected = "Package " + pkg + " new state: " + (disabled ? "disabled-user" : "enabled");
        for (String line : output.split("\\r?\\n")) {
            if (expected.equals(line.trim())) return true;
        }
        return false;
    }
}

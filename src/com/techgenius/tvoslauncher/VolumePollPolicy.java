package com.techgenius.tvoslauncher;

/** Native callbacks need no idle polling; dump fallback polls slowly until a key is held. */
final class VolumePollPolicy {
    private VolumePollPolicy() { }

    static boolean needsAudioDump(boolean nativeListener, int mode) {
        return !nativeListener || mode == 2;
    }

    static long delayMillis(boolean nativeListener, int mode, boolean held) {
        if (held) return mode == 3 ? 10 : 45;
        return needsAudioDump(nativeListener, mode) ? 400 : 0;
    }
}

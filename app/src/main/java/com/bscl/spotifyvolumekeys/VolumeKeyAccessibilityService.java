package com.bscl.spotifyvolumekeys;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class VolumeKeyAccessibilityService extends AccessibilityService {
    private static final String PREFS = "playkey_preferences";
    private static final String PREF_ENABLED = "controls_enabled";
    private static final String PREF_REVERSED = "controls_reversed";
    private static final String PREF_HOLD_MS = "hold_threshold_ms";
    private static final int DEFAULT_HOLD_MS = 650;

    private final Map<Integer, PressState> activePresses = new HashMap<>();

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN) return false;

        if (event.getAction() == KeyEvent.ACTION_UP) {
            PressState press = activePresses.remove(key);
            if (press == null) return false;
            if (!press.skipTriggered) {
                long heldFor = Math.max(0L, event.getEventTime() - press.startedAt);
                if (heldFor >= holdThresholdMs()) skipTrack(press.controller, key);
                else adjustMediaVolume(key);
            }
            return true;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() == 0) {
            if (!isControlEnabled()) return false;
            MediaController spotify = SpotifySessionController.findActiveSpotify(this);
            if (spotify == null) return false;
            activePresses.put(key, new PressState(event.getEventTime(), spotify));
            return true;
        }

        PressState press = activePresses.get(key);
        if (press == null) return false;
        if (!press.skipTriggered && event.getEventTime() - press.startedAt >= holdThresholdMs()) {
            press.skipTriggered = true;
            skipTrack(press.controller, key);
        }
        return true;
    }

    private boolean isControlEnabled() {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        return preferences.getBoolean(PREF_ENABLED, false);
    }

    private long holdThresholdMs() {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        return preferences.getInt(PREF_HOLD_MS, DEFAULT_HOLD_MS);
    }

    private void skipTrack(MediaController controller, int key) {
        boolean reversed = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_REVERSED, false);
        boolean next = (key == KeyEvent.KEYCODE_VOLUME_UP) != reversed;
        try {
            if (next) controller.getTransportControls().skipToNext();
            else controller.getTransportControls().skipToPrevious();
        } catch (RuntimeException ignored) {
            // A media app can close its session between the key press and release.
        }
    }

    private void adjustMediaVolume(int key) {
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio == null) return;
        int direction = key == KeyEvent.KEYCODE_VOLUME_UP
            ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() {
        activePresses.clear();
    }

    private static final class PressState {
        private final long startedAt;
        private final MediaController controller;
        private boolean skipTriggered;

        private PressState(long startedAt, MediaController controller) {
            this.startedAt = startedAt;
            this.controller = controller;
        }
    }
}

package com.bscl.spotifyvolumekeys;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String PREFS = "playkey_preferences";
    private static final String PREF_ENABLED = "controls_enabled";
    private static final String PREF_REVERSED = "controls_reversed";
    private static final String PREF_HOLD_MS = "hold_threshold_ms";
    private static final int DEFAULT_HOLD_MS = 650;

    private SharedPreferences preferences;
    private Switch enabledSwitch;
    private Switch reverseSwitch;
    private SeekBar thresholdSeekBar;
    private TextView thresholdLabel;
    private TextView notificationStatus;
    private TextView accessibilityStatus;
    private TextView screenOffStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);

        enabledSwitch = findViewById(R.id.switch_enabled);
        reverseSwitch = findViewById(R.id.switch_reverse);
        thresholdSeekBar = findViewById(R.id.seek_threshold);
        thresholdLabel = findViewById(R.id.text_threshold);
        notificationStatus = findViewById(R.id.text_notification_status);
        accessibilityStatus = findViewById(R.id.text_accessibility_status);
        screenOffStatus = findViewById(R.id.text_screen_off_status);

        enabledSwitch.setChecked(preferences.getBoolean(PREF_ENABLED, false));
        reverseSwitch.setChecked(preferences.getBoolean(PREF_REVERSED, false));
        int threshold = preferences.getInt(PREF_HOLD_MS, DEFAULT_HOLD_MS);
        thresholdSeekBar.setProgress(Math.max(0, Math.min(8, (threshold - 350) / 100)));
        updateThresholdLabel(thresholdSeekBar.getProgress());

        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PREF_ENABLED, checked).apply();
            if (checked) ScreenOffVolumeController.register(this);
            else ScreenOffVolumeController.unregister(this);
        });
        reverseSwitch.setOnCheckedChangeListener((button, checked) ->
            preferences.edit().putBoolean(PREF_REVERSED, checked).apply());
        thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateThresholdLabel(progress);
                if (fromUser) preferences.edit().putInt(PREF_HOLD_MS, 350 + progress * 100).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        Button notificationButton = findViewById(R.id.button_notification_access);
        notificationButton.setOnClickListener(view -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        Button accessibilityButton = findViewById(R.id.button_accessibility);
        accessibilityButton.setOnClickListener(view -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    private void updateThresholdLabel(int progress) {
        thresholdLabel.setText(getString(R.string.threshold_format, 350 + progress * 100));
    }

    private void refreshPermissionStatus() {
        boolean notificationEnabled = hasNotificationAccess();
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean screenOffGranted = ScreenOffVolumeController.hasPermission(this);

        notificationStatus.setText(notificationEnabled
            ? R.string.notification_status_on : R.string.notification_status_off);
        accessibilityStatus.setText(accessibilityEnabled
            ? R.string.accessibility_status_on : R.string.accessibility_status_off);
        screenOffStatus.setText(screenOffGranted
            ? R.string.screen_off_status_on : R.string.screen_off_status_off);

        enabledSwitch.setEnabled(notificationEnabled && accessibilityEnabled);
        if (screenOffGranted && preferences.getBoolean(PREF_ENABLED, false)) {
            ScreenOffVolumeController.register(this);
        }
    }

    private boolean hasNotificationAccess() {
        ComponentName listener = new ComponentName(this, PlaybackNotificationListener.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            return manager != null && manager.isNotificationListenerAccessGranted(listener);
        }
        return containsEnabledComponent("enabled_notification_listeners", listener);
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName service = new ComponentName(this, VolumeKeyAccessibilityService.class);
        return containsEnabledComponent(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, service);
    }

    private boolean containsEnabledComponent(String setting, ComponentName target) {
        String enabled = Settings.Secure.getString(getContentResolver(), setting);
        if (enabled == null) return false;
        for (String entry : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(entry);
            if (target.equals(component)) return true;
        }
        return false;
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}

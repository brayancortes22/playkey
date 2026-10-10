package com.bscl.spotifyvolumekeys;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ScreenOffVolumeController {
    public static final String PERMISSION_SCREEN_OFF =
        "android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER";
    private static final String PREFS = "playkey_preferences";
    private static final String PREF_ENABLED = "controls_enabled";
    private static final String PREF_REVERSED = "controls_reversed";
    private static boolean isRegistered = false;

    private ScreenOffVolumeController() { }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(PERMISSION_SCREEN_OFF) == PackageManager.PERMISSION_GRANTED;
    }

    public static synchronized void register(Context context) {
        if (isRegistered || !hasPermission(context)) return;
        try {
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            if (manager == null) return;
            Class<?> listenerClass = findListenerInterface();
            if (listenerClass == null) return;

            Context appContext = context.getApplicationContext();
            Object proxy = Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass },
                (proxyObj, method, args) -> {
                    if ("onVolumeKeyLongPress".equals(method.getName()) && args != null && args.length > 0) {
                        if (args[0] instanceof KeyEvent) {
                            handleLongPress(appContext, (KeyEvent) args[0]);
                        }
                        return null;
                    }
                    if ("equals".equals(method.getName())) return proxyObj == args[0];
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxyObj);
                    return null;
                }
            );

            Method setListener = MediaSessionManager.class.getMethod(
                "setOnVolumeKeyLongPressListener",
                listenerClass,
                Handler.class
            );
            setListener.invoke(manager, proxy, new Handler(Looper.getMainLooper()));
            isRegistered = true;
        } catch (Exception ignored) {
            isRegistered = false;
        }
    }

    public static synchronized void unregister(Context context) {
        if (!isRegistered) return;
        try {
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            Class<?> listenerClass = findListenerInterface();
            if (manager != null && listenerClass != null) {
                Method setListener = MediaSessionManager.class.getMethod(
                    "setOnVolumeKeyLongPressListener",
                    listenerClass,
                    Handler.class
                );
                setListener.invoke(manager, null, null);
            }
        } catch (Exception ignored) { }
        isRegistered = false;
    }

    private static void handleLongPress(Context context, KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_ENABLED, false)) return;

        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlayKey:SkipWakeLock");
            wakeLock.acquire(1000L);
        }

        try {
            MediaController spotify = SpotifySessionController.findActiveSpotify(context);
            if (spotify != null) {
                boolean reversed = prefs.getBoolean(PREF_REVERSED, false);
                boolean next = (keyCode == KeyEvent.KEYCODE_VOLUME_UP) != reversed;
                if (next) spotify.getTransportControls().skipToNext();
                else spotify.getTransportControls().skipToPrevious();
                vibrate(context);
            }
        } catch (Exception ignored) {
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private static void vibrate(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception ignored) { }
    }

    private static Class<?> findListenerInterface() {
        for (Class<?> inner : MediaSessionManager.class.getDeclaredClasses()) {
            if ("OnVolumeKeyLongPressListener".equals(inner.getSimpleName())) return inner;
        }
        try {
            return Class.forName("android.media.session.MediaSessionManager$OnVolumeKeyLongPressListener");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}

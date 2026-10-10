package com.bscl.spotifyvolumekeys;

import android.service.notification.NotificationListenerService;

/** Gives Android permission to expose active media sessions and register screen-off volume listener. */
public final class PlaybackNotificationListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        ScreenOffVolumeController.register(this);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        ScreenOffVolumeController.unregister(this);
    }
}


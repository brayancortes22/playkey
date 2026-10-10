package com.bscl.spotifyvolumekeys;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;

import java.util.List;

final class SpotifySessionController {
    private static final String SPOTIFY_PACKAGE = "com.spotify.music";

    private SpotifySessionController() { }

    static MediaController findActiveSpotify(Context context) {
        MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
        if (manager == null) return null;
        ComponentName listener = new ComponentName(context, PlaybackNotificationListener.class);
        try {
            List<MediaController> sessions = manager.getActiveSessions(listener);
            for (MediaController session : sessions) {
                if (SPOTIFY_PACKAGE.equals(session.getPackageName())) return session;
            }
        } catch (SecurityException | IllegalStateException ignored) {
            return null;
        }
        return null;
    }
}

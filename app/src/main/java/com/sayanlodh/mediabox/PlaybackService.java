package com.sayanlodh.mediabox;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** Background audio, media notification and a timer that survives leaving the UI. */
public final class PlaybackService extends MediaSessionService {
    public static final String ACTION_SLEEP = "com.sayanlodh.mediabox.SET_SLEEP";
    public static final String ACTION_CANCEL_SLEEP = "com.sayanlodh.mediabox.CANCEL_SLEEP";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String PREFS = "sayan_player_settings";
    public static final String SLEEP_END = "sleep_end_epoch";

    private ExoPlayer player;
    private MediaSession mediaSession;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sleepCallback;

    @Override public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL);
        mediaSession = new MediaSession.Builder(this, player).build();
        long savedEnd = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(SLEEP_END, 0);
        if (savedEnd > System.currentTimeMillis()) {
            scheduleSleep(savedEnd - System.currentTimeMillis());
        } else {
            clearSleep();
        }
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SLEEP.equals(intent.getAction())) {
            int minutes = Math.max(1, intent.getIntExtra(EXTRA_MINUTES, 15));
            long delay = minutes * 60_000L;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(SLEEP_END, System.currentTimeMillis() + delay).apply();
            scheduleSleep(delay);
            return START_STICKY;
        }
        if (intent != null && ACTION_CANCEL_SLEEP.equals(intent.getAction())) {
            clearSleep();
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void scheduleSleep(long delayMillis) {
        if (sleepCallback != null) handler.removeCallbacks(sleepCallback);
        final long due = SystemClock.elapsedRealtime() + delayMillis;
        sleepCallback = () -> {
            if (SystemClock.elapsedRealtime() >= due) {
                if (player != null) player.pause();
                clearSleep();
            }
        };
        handler.postDelayed(sleepCallback, delayMillis);
    }

    private void clearSleep() {
        if (sleepCallback != null) handler.removeCallbacks(sleepCallback);
        sleepCallback = null;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(SLEEP_END).apply();
    }

    @Override public void onDestroy() {
        if (sleepCallback != null) handler.removeCallbacks(sleepCallback);
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}

package my.mmu.rssnewsreader.service.tts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

public abstract class PlayerAdapter {

    private static final String TAG = "PlayerAdapter";
    private static final IntentFilter AUDIO_NOISY_INTENT_FILTER =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private boolean mAudioNoisyReceiverRegistered = false;
    private final BroadcastReceiver mAudioNoisyReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        if (isPlayingMediaPlayer()) {
                            pauseMediaPlayer();
                        }
                        if (isPlaying()) {
                            pause();
                        }
                    }
                }
            };

    private final Context mApplicationContext;
    private final AudioManager mAudioManager;
    private final AudioFocusHelper mAudioFocusHelper;

    private boolean mPlayOnAudioFocus = false;

    public PlayerAdapter(@NonNull Context context) {
        mApplicationContext = context.getApplicationContext();
        mAudioManager = (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
        mAudioFocusHelper = new AudioFocusHelper();
    }

    public abstract boolean isPlaying();

    public abstract boolean isPlayingMediaPlayer();

    public void play() {
        if (mAudioFocusHelper.requestAudioFocus()) {
            registerAudioNoisyReceiver();
            onPlay();
        } else {
            Log.w(TAG, "Audio focus request denied");
        }
    }

    protected abstract void onPlay();

    public abstract void playMediaPlayer();

    public abstract void pauseMediaPlayer();

    public final void pause() {
        if (!mPlayOnAudioFocus) {
            mAudioFocusHelper.abandonAudioFocus();
        }
        unregisterAudioNoisyReceiver();
        onPause();
    }

    protected abstract void onPause();

    public final void stop() {
        mAudioFocusHelper.abandonAudioFocus();
        unregisterAudioNoisyReceiver();
        onStop();
    }

    protected abstract void onStop();

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mApplicationContext.registerReceiver(mAudioNoisyReceiver, AUDIO_NOISY_INTENT_FILTER);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mApplicationContext.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    private final class AudioFocusHelper
            implements AudioManager.OnAudioFocusChangeListener {

        private AudioFocusRequest mAudioFocusRequest;

        private boolean requestAudioFocus() {
            Log.d(TAG, "Requesting audio focus...");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (mAudioFocusRequest == null) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();

                    mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(audioAttributes)
                            .setAcceptsDelayedFocusGain(false) // Simplified to avoid silent failures
                            .setOnAudioFocusChangeListener(this)
                            .build();
                }
                int result = mAudioManager.requestAudioFocus(mAudioFocusRequest);
                Log.d(TAG, "AudioFocusRequest result: " + result);
                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                int result = mAudioManager.requestAudioFocus(this,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                Log.d(TAG, "Legacy AudioFocusRequest result: " + result);
                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
        }

        private void abandonAudioFocus() {
            Log.d(TAG, "Abandoning audio focus");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (mAudioFocusRequest != null) {
                    mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
                }
            } else {
                mAudioManager.abandonAudioFocus(this);
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "onAudioFocusChange: " + focusChange);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mPlayOnAudioFocus && !isPlaying()) {
                        Log.d(TAG, "Regained focus, resuming playback");
                        playMediaPlayer();
                        play();
                    } else {
                        Log.d(TAG, "Regained focus but not resuming (mPlayOnAudioFocus=" + mPlayOnAudioFocus + ")");
                    }
                    mPlayOnAudioFocus = false;
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.d(TAG, "Transient loss (duckable) - pausing");
                    pauseMediaPlayer();
                    if (isPlaying()) {
                        mPlayOnAudioFocus = true;
                        pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(TAG, "Transient loss - pausing");
                    pauseMediaPlayer();
                    if (isPlaying()) {
                        mPlayOnAudioFocus = true;
                        pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(TAG, "Permanent loss - pausing and keeping flag for potential resume");
                    pauseMediaPlayer();
                    if (isPlaying()) {
                        mPlayOnAudioFocus = true;
                        pause();
                    }
                    break;
            }
        }
    }
}
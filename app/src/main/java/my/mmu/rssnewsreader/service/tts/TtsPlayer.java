package my.mmu.rssnewsreader.service.tts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import my.mmu.rssnewsreader.R;
import my.mmu.rssnewsreader.data.entry.EntryRepository;
import my.mmu.rssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.rssnewsreader.ui.webview.WebViewActivity;
import my.mmu.rssnewsreader.ui.webview.WebViewListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.Text;

import java.io.File;
import java.io.IOException;
import java.text.BreakIterator;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;


@Singleton
public class TtsPlayer extends PlayerAdapter implements TtsPlayerListener {

    public static final String TAG = TtsPlayer.class.getSimpleName();

    private TextToSpeech tts;
    private PlaybackStateListener listener;
    private MediaSessionCompat.Callback callback;
    private WebViewListener webViewCallback;
    private Context context;
    private final TtsExtractor ttsExtractor;
    private final EntryRepository entryRepository;
    private final SharedPreferencesRepository sharedPreferencesRepository;

    private int sentenceCounter;
    private List<String> sentences = new ArrayList<>();

    private CountDownLatch countDownLatch;
    private int currentState;
    private long currentId = 0;
    private long feedId = 0;
    private String language;
    private boolean isInit = false;
    private boolean isPreparing = false;
    private boolean actionNeeded = false;
    private boolean isPausedManually;
    private boolean webViewConnected = false;
    private boolean uiControlPlayback = false;
    private boolean isManualSkip = false;
    private boolean isArticleFinished = false;
    private boolean isSettingUpNewArticle = false;
    private MediaPlayer mediaPlayer; // Background music player
    private MediaPlayer ttsMediaPlayer; // TTS audio player
    private File ttsFile;
    private int currentLoadedSentenceIndex = -1;
    
    private String currentUtteranceID = null;
    private boolean hasSpokenAfterSetup = false;
    private PlaybackUiListener playbackUiListener;
    private int currentExtractProgress = 0;

    private List<String> availableTtsEngines;
    private int currentTtsEngineIndex = -1;

    @Inject
    public TtsPlayer(@ApplicationContext Context context, TtsExtractor ttsExtractor, EntryRepository entryRepository, SharedPreferencesRepository sharedPreferencesRepository) {
        super(context);
        this.ttsExtractor = ttsExtractor;
        this.entryRepository = entryRepository;
        this.sharedPreferencesRepository = sharedPreferencesRepository;
        this.context = context;
        this.isPausedManually = sharedPreferencesRepository.getIsPausedManually();
        
        ttsMediaPlayer = new MediaPlayer();
        ttsMediaPlayer.setOnCompletionListener(mp -> {
            Log.d(TAG, "ttsMediaPlayer completed sentence: " + sentenceCounter);
            if (isManualSkip) {
                isManualSkip = false;
                return;
            }
            
            if (isArticleFinished) return;

            if (sentences != null && sentenceCounter < sentences.size() - 1) {
                sentenceCounter++;
                entryRepository.updateSentCount(sentenceCounter, currentId);
                speak(); 
            } else {
                Log.d(TAG, "Finished last sentence. Moving to next article.");
                entryRepository.updateSentCount(0, currentId);
                sentenceCounter = 0;
                isArticleFinished = true;
                if (callback != null) {
                    callback.onSkipToNext();
                }
            }
        });
        ttsFile = new File(context.getCacheDir(), "tts_temp.wav");
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void initTts(TtsService ttsService, PlaybackStateListener listener, MediaSessionCompat.Callback callback) {
        this.listener = listener;
        this.callback = callback;

        if (tts != null) {
            Log.w(TAG, "TTS already exists, shutting down old instance first");
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down old TTS", e);
            }
            tts = null;
            isInit = false;
        }

        availableTtsEngines = null;
        currentTtsEngineIndex = -1;
        initializeTtsEngine(ttsService, null);
    }

    private void initializeTtsEngine(Context ttsContext, String enginePackage) {
        Log.d(TAG, "Initializing TTS with engine: " + (enginePackage == null ? "DEFAULT" : enginePackage));
        
        tts = new TextToSpeech(ttsContext, status -> {
            Log.d(TAG, "TTS init callback received with status: " + status + " (SUCCESS=0, ERROR=" + TextToSpeech.ERROR + ")");

            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "initTts successful - TTS engine bound");

                try {
                    String defaultEngine = tts.getDefaultEngine();
                    Log.d(TAG, "Default TTS engine: " + defaultEngine);

                    List<TextToSpeech.EngineInfo> enginesList = tts.getEngines();
                    Log.d(TAG, "Total engines available: " + enginesList.size());
                    for (TextToSpeech.EngineInfo engine : enginesList) {
                        Log.d(TAG, "Engine: " + engine.name + " (label: " + engine.label + ")");
                    }

                    if (defaultEngine == null || defaultEngine.isEmpty()) {
                        Log.e(TAG, "TTS initialized but default engine is null!");
                        if (!enginesList.isEmpty()) {
                            String fallbackEngine = enginesList.get(0).name;
                            Log.d(TAG, "Attempting to use fallback engine: " + fallbackEngine);
                            isInit = true;
                        } else {
                            // Try next engine if available
                            Log.w(TAG, "Default engine null and no internal engines reported. Trying next available system engine...");
                            attemptNextTtsEngine(ttsContext);
                            return;
                        }
                    } else {
                        isInit = true;
                    }

                    Locale[] testLocales = {Locale.ENGLISH, Locale.US, Locale.getDefault()};
                    boolean languageFound = false;

                    for (Locale testLocale : testLocales) {
                        int available = tts.isLanguageAvailable(testLocale);
                        Log.d(TAG, "Language " + testLocale + " availability: " + available + " (LANG_AVAILABLE=0, LANG_COUNTRY_AVAILABLE=1, LANG_COUNTRY_VAR_AVAILABLE=2)");

                        if (available >= TextToSpeech.LANG_AVAILABLE) {
                            languageFound = true;
                            Log.d(TAG, "Found working language: " + testLocale);
                            break;
                        }
                    }

                    if (!languageFound) {
                        Log.w(TAG, "No languages available, but TTS may still work. Proceeding...");
                    }

                    Log.d(TAG, "TTS fully initialized and ready");
                } catch (Exception e) {
                    Log.e(TAG, "Error verifying TTS engine", e);
                    attemptNextTtsEngine(ttsContext);
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    int result = tts.setAudioAttributes(audioAttributes);
                    Log.d(TAG, "TTS audio attributes set, result: " + (result == TextToSpeech.SUCCESS ? "SUCCESS" : "ERROR"));
                } else {
                    Log.d(TAG, "Android version < LOLLIPOP, using legacy audio stream");
                }

                if (actionNeeded) {
                    Log.d(TAG, "Deferred auto-play activated — TTS is now ready");
                    setupTts();
                    if (!isPausedManually) {
                        speak();
                    } else {
                        Log.d(TAG, "Deferred play skipped due to manual pause");
                    }
                    actionNeeded = false;
                }
            } else {
                Log.e(TAG, "TTS initialization FAILED with status: " + status);
                attemptNextTtsEngine(ttsContext);
            }
        }, enginePackage);

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS onStart (Synthesis) - utteranceId: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS onDone (Synthesis complete) - utteranceId: " + utteranceId);
                
                String expectedId = "utterance_" + currentId + "_" + sentenceCounter;
                if (!expectedId.equals(utteranceId)) {
                    Log.w(TAG, "Ignoring onDone for stale utterance: " + utteranceId + " (expected: " + expectedId + ")");
                    return;
                }
                
                new Handler(Looper.getMainLooper()).post(() -> playTtsFile());
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS ERROR - utteranceId: " + utteranceId);
                Log.e(TAG, "TTS engine state - isInit: " + isInit + ", isSpeaking: " + (tts != null && tts.isSpeaking()));

                if (tts != null) {
                    Log.e(TAG, "TTS engine info - Engines available: " + tts.getEngines());
                    Log.e(TAG, "Current language: " + language);
                }

                if (webViewCallback != null) {
                    webViewCallback.makeSnackbar("TTS playback error. Please check TTS settings.");
                }
            }
        });
    }

    private void attemptNextTtsEngine(Context ttsContext) {
        if (availableTtsEngines == null) {
            availableTtsEngines = getAvailableTtsEngines(ttsContext);
            currentTtsEngineIndex = -1;
        }

        currentTtsEngineIndex++;
        if (currentTtsEngineIndex < availableTtsEngines.size()) {
            String nextEngine = availableTtsEngines.get(currentTtsEngineIndex);
            Log.d(TAG, "Attempting fallback to next TTS engine: " + nextEngine);
            
            // Shutdown previous instance if any
            if (tts != null) {
                try {
                    tts.shutdown();
                } catch (Exception e) { /* ignore */ }
            }
            
            initializeTtsEngine(ttsContext, nextEngine);
        } else {
            Log.e(TAG, "All TTS engines failed initialization.");
            isInit = false;
            if (webViewCallback != null) {
                webViewCallback.makeSnackbar("TTS init failed. Please check system TTS settings.");
            }
        }
    }

    private List<String> getAvailableTtsEngines(Context context) {
        List<String> engines = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
            List<ResolveInfo> resolveInfos = pm.queryIntentServices(intent, PackageManager.MATCH_ALL);
            for (ResolveInfo info : resolveInfos) {
                if (info.serviceInfo != null) {
                    engines.add(info.serviceInfo.packageName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying TTS services", e);
        }
        Log.d(TAG, "Found system TTS engines: " + engines);
        return engines;
    }

    public interface PlaybackUiListener {
        void onPlaybackStarted();
        void onPlaybackPaused();
    }

    public void setPlaybackUiListener(PlaybackUiListener listener) {
        this.playbackUiListener = listener;
    }

    public void stopTtsPlayback() {
        if (ttsMediaPlayer != null) {
            if (ttsMediaPlayer.isPlaying()) ttsMediaPlayer.stop();
            ttsMediaPlayer.reset();
        }
        if (tts != null) {
            try {
                tts.stop();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "TTS not bound when stopping playback", e);
                isInit = false;
            }
        }

        currentId = -1;
        isPausedManually = false;
        isPreparing = false;
        isArticleFinished = false;
        isSettingUpNewArticle = false;
        currentLoadedSentenceIndex = -1;
        setUiControlPlayback(false);
        if (playbackUiListener != null) {
            playbackUiListener.onPlaybackPaused();
        }
    }

    public void pauseTts() {
        pauseTts(true);
    }

    public void pauseTts(boolean fromUser) {
        if (ttsMediaPlayer != null && ttsMediaPlayer.isPlaying()) {
            ttsMediaPlayer.pause();
        }
        if (tts != null) tts.stop();

        if (fromUser) {
            isPausedManually = true;
            sharedPreferencesRepository.setIsPausedManually(true);
        }

        setNewState(PlaybackStateCompat.STATE_PAUSED);
        setUiControlPlayback(false);
        if (playbackUiListener != null) {
            playbackUiListener.onPlaybackPaused();
        }
    }

    public void extract(long currentId, long feedId, String content, String language) {
        Log.d(TAG, "Switching to new article: ID=" + currentId);

        // Safety checks
        if (currentId <= 0) {
            Log.e(TAG, "Invalid article ID: " + currentId);
            return;
        }

        boolean wasSpeaking = ttsMediaPlayer != null && ttsMediaPlayer.isPlaying();
        isPausedManually = !wasSpeaking && sharedPreferencesRepository.getIsPausedManually();
        sharedPreferencesRepository.setIsPausedManually(isPausedManually);
        Log.d(TAG, "Detected isPausedManually = " + isPausedManually);

        if (ttsMediaPlayer != null) {
            if (ttsMediaPlayer.isPlaying()) ttsMediaPlayer.stop();
            ttsMediaPlayer.reset();
        }
        if (tts != null) tts.stop();

        // Reset state
        isPreparing = true;
        isSettingUpNewArticle = true;
        if (sentences != null) {
            sentences.clear();
        }
        isArticleFinished = false;
        currentLoadedSentenceIndex = -1;

        this.language = language;
        this.currentId = currentId;
        this.feedId = feedId;
        hasSpokenAfterSetup = false;
        countDownLatch = new CountDownLatch(1);

        if (language != null && !language.isEmpty() && ttsExtractor != null) {
            ttsExtractor.setCurrentLanguage(language, true);
            Log.d(TAG, "[extract] Locked language = " + language);
        }

        if (content != null && !content.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    extractToTts(content, language);
                    countDownLatch.await();

                    if (isInit) {
                        setupTts();
                        if (!isPausedManually) {
                            Log.d(TAG, "TTS ready and not manually paused — auto speaking");
                            ContextCompat.getMainExecutor(context).execute(() -> play());
                        } else {
                            Log.d(TAG, "TTS ready but paused manually — not speaking");
                        }
                    } else {
                        Log.d(TAG, "TTS not initialized yet, setting actionNeeded = true");
                        actionNeeded = true;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for extraction", e);
                } catch (Exception e) {
                    Log.e(TAG, "Exception during extraction", e);
                }
            }).start();
        } else {
            if (ttsExtractor != null) {
                ttsExtractor.setCallback(this);
                ttsExtractor.prioritize();
            } else {
                Log.e(TAG, "TtsExtractor is null");
            }
        }
    }

    @Override
    public void extractToTts(String content, String language) {
        if (tts == null) {
            Log.w(TAG, "TTS engine is not initialized.");
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            Log.w(TAG, "extractToTts: No content provided.");
            return;
        }

        sentences.clear();

        String[] raw = content.split(Pattern.quote(ttsExtractor.delimiter));
        List<String> sentenceList = new ArrayList<>(raw.length);
        for(String part : raw) {
            String trimmed = part.trim();
            if(! trimmed.isEmpty()) {
                sentenceList.add(trimmed);
            }
        }
        int totalParagraphs = sentenceList.size();

        Locale localeForBreakIterator = Locale.ENGLISH;
        if (language != null && !language.isEmpty()) {
            try {
                localeForBreakIterator = new Locale(language);
            } catch (Exception e) {
                Log.w(TAG, "Invalid language for BreakIterator: " + language);
            }
        }
        final Locale iteratorLocale = localeForBreakIterator;

        new Thread(() -> {
            for (int i = 0; i < sentenceList.size(); i++) {
                String paragraph = sentenceList.get(i);

                // Always split paragraphs into sentences to ensure finer granularity
                // This prevents the "whole paragraph restart" issue on pause/resume
                BreakIterator iterator = BreakIterator.getSentenceInstance(iteratorLocale);
                iterator.setText(paragraph);
                int start = iterator.first();
                for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
                    String sentence = paragraph.substring(start, end).trim();
                    if (!sentence.isEmpty()) {
                        sentences.add(sentence);
                    }
                }

                // Update progress based on paragraphs processed
                currentExtractProgress = Math.min((int) (((double) (i + 1) / totalParagraphs) * 100), 95);

                if (i % 3 == 0 || i == sentenceList.size() - 1) {
                    ContextCompat.getMainExecutor(context).execute(() -> {
                        if (webViewCallback != null) {
                            webViewCallback.updateLoadingProgress(currentExtractProgress);
                        }
                    });
                }
            }

            if (sentences.size() < 2) {
                if (webViewCallback != null) webViewCallback.askForReload(feedId);
                sentences.clear();
                actionNeeded = false;
                return;
            } else {
                int savedProgress = entryRepository.getSentCount(currentId);
                sentenceCounter = Math.min(savedProgress, sentences.size() - 1);

                if (!isInit) {
                    Log.d(TAG, "TTS not initialized yet");
                    actionNeeded = true;
                } else {
                    Log.d(TAG, "TTS is initialized");
                    setupTts();
                }
            }
            countDownLatch.countDown();
        }).start();
    }

    private void setupTts() {
        ContextCompat.getMainExecutor(context).execute(() -> {
            Log.d(TAG, "[setupTts] currentLanguage = " + language + ", isLockedByTtsPlayer = " + ttsExtractor.isLocked() + ", ttsExtractor.language = " + ttsExtractor.getCurrentLanguage());
            if (sentences == null || sentences.isEmpty()) {
                Log.w(TAG, "No content to read in setupTts(), skipping...");
                return;
            }

            isPreparing = false;

            if (webViewCallback != null) {
                webViewCallback.finishedSetup();
                webViewCallback.updateLoadingProgress(100);
                webViewCallback.hideFakeLoading();
            }

            if (webViewCallback instanceof WebViewActivity) {
                ((WebViewActivity) webViewCallback).syncLoadingWithTts();
            }

            if (language == null || language.isEmpty()) {
                Log.w(TAG, "Warning: Language is null or empty, defaulting to English.");
                language = "en";
            }

            try {
                Log.d(TAG, "Setting TTS language to: " + language);
                Log.d(TAG, "setupTts() using language: " + language);
                setLanguage(new Locale(language), true);
            } catch (Exception e) {
                Log.d(TAG, "Invalid locale " + e.getMessage());
                setLanguage(Locale.ENGLISH, true);
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isSettingUpNewArticle = false;

                if (sentences.size() > 0 && !isPausedManually && !hasSpokenAfterSetup) {
                    hasSpokenAfterSetup = true;
                    play();
                    if (webViewCallback != null) {
                        Log.d(TAG, "Hiding fake loading after TTS starts.");
                        webViewCallback.hideFakeLoading();
                    } else {
                        Log.w(TAG, "webViewCallback is null, cannot hideFakeLoading.");
                    }
                } else {
                    Log.d(TAG, "TTS ready, but paused manually or no content. Waiting for user to resume.");
                }
            }, 200);
        });
    }

    private void identifyLanguage(String sentence, boolean fromService) {
        float confidenceThreshold = (float) sharedPreferencesRepository.getConfidenceThreshold() / 100;

        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient(
                new LanguageIdentificationOptions.Builder()
                        .setConfidenceThreshold(confidenceThreshold)
                        .build());
        languageIdentifier.identifyLanguage(sentence)
                .addOnSuccessListener(languageCode -> {
                    if (languageCode.equals("und")) {
                        Log.i(TAG, "Can't identify language.");
                        setLanguage(Locale.ENGLISH, fromService);
                    } else {
                        Log.i(TAG, "Language: " + languageCode);
                        setLanguage(new Locale(languageCode), fromService);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Language identification failed (likely due to missing GMS or network): " + e.getMessage());
                    // Fallback to English so the app doesn't hang
                    setLanguage(Locale.ENGLISH, fromService);
                });
    }

    private void setLanguage(Locale locale, boolean fromService) {
        if (tts == null) {
            Log.w(TAG, "TTS is null, cannot set language to: " + locale);
            return;
        }
        
        if (locale == null) {
            Log.w(TAG, "Locale is null, cannot set language");
            return;
        }
        
        synchronized (this) {
            // Check if the TTS engine is available
            int engineCheck = tts.isLanguageAvailable(locale);
            Log.d(TAG, "Language availability check for " + locale + ": " + engineCheck);
            
            int result = tts.setLanguage(locale);

            Log.d(TAG, "setLanguage() called with: " + locale.toString());
            Log.d(TAG, "setLanguage() result: " + result);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language not supported: " + locale);
                if (webViewCallback != null) {
                    webViewCallback.makeSnackbar("Language not installed. Required language: " + locale.getDisplayLanguage());
                }
                // Try to set to English as fallback
                int fallbackCheck = tts.isLanguageAvailable(Locale.ENGLISH);
                Log.d(TAG, "English availability: " + fallbackCheck);
                
                int fallbackResult = tts.setLanguage(Locale.ENGLISH);
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Even English language is not supported");
                    if (webViewCallback != null) {
                        webViewCallback.makeSnackbar("TTS engine error: No supported language found");
                    }
                } else {
                    Log.d(TAG, "Fallback to English successful");
                }
            }
            else {
                Log.d(TAG, "Language successfully set to: " + locale);
            }

            if (fromService && callback != null) {
                callback.onCustomAction("playFromService", null);
            } else {
                Log.d(TAG, "Language set. Waiting for speak() to be called.");
            }
        }
    }

    private void setMediaPlayerAttributes() {
        if (ttsMediaPlayer != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                ttsMediaPlayer.setAudioAttributes(audioAttributes);
            }
        }
    }

    public void speak() {
        if (isSettingUpNewArticle) {
            Log.d(TAG, "TTS setup in progress, skipping speak()");
            return;
        }

        if (!isInit || tts == null) {
            Log.d(TAG, "speak() skipped — TTS not initialized yet. Waiting for init.");
            actionNeeded = true;
            return;
        }

        // Check if resuming the currently loaded sentence
        if (ttsMediaPlayer != null && sentenceCounter == currentLoadedSentenceIndex && !ttsMediaPlayer.isPlaying() && ttsMediaPlayer.getCurrentPosition() > 0 && !isPausedManually) {
             Log.d(TAG, "Resuming current sentence from position: " + ttsMediaPlayer.getCurrentPosition());
             ttsMediaPlayer.start();
             setUiControlPlayback(true);
             setNewState(PlaybackStateCompat.STATE_PLAYING);
             if (playbackUiListener != null) playbackUiListener.onPlaybackStarted();
             return;
        }

        if (isPausedManually) {
            Log.d(TAG, "speak() called but isPausedManually=true, will not speak");
            return;
        }

        if (sentences == null || sentences.isEmpty()) {
            Log.d(TAG, "No sentences loaded, cannot play");
            return;
        }

        if (sentenceCounter < 0) sentenceCounter = 0;
        if (sentenceCounter >= sentences.size()) return;

        try {
            String sentence = sentences.get(sentenceCounter);
            if (sentence == null || sentence.trim().isEmpty()) {
                sentenceCounter++;
                speak();
                return;
            }

            if (language == null) {
                identifyLanguage(sentence, false);
                return;
            } else {
                Log.d(TAG, "Synthesizing to file [#" + sentenceCounter + "]: " + sentence);

                // Reset player so it doesn't interfere
                if(ttsMediaPlayer != null) {
                    ttsMediaPlayer.reset();
                    setMediaPlayerAttributes();
                }
                currentLoadedSentenceIndex = sentenceCounter;

                String utteranceId = "utterance_" + currentId + "_" + sentenceCounter;
                Bundle params = new Bundle();
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC);

                int result = tts.synthesizeToFile(sentence, params, ttsFile, utteranceId);

                if (result == TextToSpeech.ERROR) {
                    Log.e(TAG, "TTS synthesizeToFile failed");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in speak(): " + e.getMessage(), e);
        }
    }

    private void playTtsFile() {
        try {
            if (ttsMediaPlayer == null) return;
            Log.d(TAG, "Playing generated TTS file: " + ttsFile.getAbsolutePath());
            ttsMediaPlayer.reset();
            setMediaPlayerAttributes();
            ttsMediaPlayer.setDataSource(ttsFile.getAbsolutePath());
            ttsMediaPlayer.prepare();
            ttsMediaPlayer.start();

            setUiControlPlayback(true);
            setNewState(PlaybackStateCompat.STATE_PLAYING);
            if (playbackUiListener != null) playbackUiListener.onPlaybackStarted();

            // Highlight
            if (sentenceCounter < sentences.size()) {
                String sentence = sentences.get(sentenceCounter);
                if (webViewCallback != null) webViewCallback.highlightText(sentence);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error playing TTS file", e);
        }
    }

    public void fastForward() {
        if (tts != null && sentenceCounter < sentences.size() - 1) {
            isManualSkip = true;
            sentenceCounter++;
            entryRepository.updateSentCount(sentenceCounter, currentId);
            
            if(ttsMediaPlayer.isPlaying()) ttsMediaPlayer.stop();
            ttsMediaPlayer.reset();
            tts.stop();
            
            speak();
        } else {
            entryRepository.updateSentCount(0, currentId);
            callback.onSkipToNext();
        }
    }

    public void fastRewind() {
        if (tts != null && sentenceCounter > 0) {
            isManualSkip = true;
            sentenceCounter--;
            entryRepository.updateSentCount(sentenceCounter, currentId);
            
            if(ttsMediaPlayer.isPlaying()) ttsMediaPlayer.stop();
            ttsMediaPlayer.reset();
            tts.stop();
            
            speak();
        }
    }

    @Override
    public boolean isPlayingMediaPlayer() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void setupMediaPlayer(boolean forced) {
        if (forced) {
            stopMediaPlayer();
        }

        if (mediaPlayer == null && sharedPreferencesRepository.getBackgroundMusic()) {
            if (sharedPreferencesRepository.getBackgroundMusicFile().equals("default")) {
                mediaPlayer = MediaPlayer.create(context, R.raw.pianomoment);
            } else {
                File savedFile = new File(context.getFilesDir(), "user_file.mp3");
                if (savedFile.exists()) {
                    mediaPlayer = MediaPlayer.create(context, Uri.parse(savedFile.getAbsolutePath()));
                } else {
                    mediaPlayer = MediaPlayer.create(context, R.raw.pianomoment);
                }
            }
            mediaPlayer.setLooping(true);
            changeMediaPlayerVolume();
        }
        playMediaPlayer();
    }

    @Override
    public void playMediaPlayer() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) mediaPlayer.start();
    }

    @Override
    public void pauseMediaPlayer() {
        if (mediaPlayer != null) mediaPlayer.pause();
    }

    public void changeMediaPlayerVolume() {
        if (mediaPlayer != null) {
            float volume = (float) sharedPreferencesRepository.getBackgroundMusicVolume() / 100;
            mediaPlayer.setVolume(volume, volume);
        }
    }

    public void stopMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public boolean isPlaying() {
        return ttsMediaPlayer != null && ttsMediaPlayer.isPlaying();
    }

    public void play() {
        Log.d(TAG, "play() called - isInit=" + isInit + ", isPausedManually=" + isPausedManually + ", sentences.size=" + (sentences != null ? sentences.size() : 0));

        if (!isInit) {
            Log.w(TAG, "TTS not initialized, cannot play");
            actionNeeded = true;
            return;
        }

        if (sentences == null || sentences.isEmpty()) {
            Log.w(TAG, "No sentences loaded, cannot play");
            return;
        }

        // Check audio volume
        android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            Log.d(TAG, "Current media volume: " + currentVolume + "/" + maxVolume);

            if (currentVolume == 0) {
                Log.w(TAG, "Media volume is MUTED! User may not hear TTS.");
                if (webViewCallback != null) {
                    webViewCallback.makeSnackbar("Media volume is muted. Please increase volume.");
                }
            }
        }

        isPausedManually = false;
        sharedPreferencesRepository.setIsPausedManually(false);

        super.play();
    }

    @Override
    protected void onPlay() {
        Log.d(TAG, "onPlay called - Focus granted, starting playback");
        if (!isSettingUpNewArticle) {
            speak();
            setNewState(PlaybackStateCompat.STATE_PLAYING);
        } else {
            Log.d(TAG, "Article setup in progress, deferring play");
            actionNeeded = true;
        }
    }

    @Override
    protected void onPause() {
        pauseTts(false);
    }

    @Override
    protected void onStop() {
        stopMediaPlayer();
        stopTtsPlayback();
        setNewState(PlaybackStateCompat.STATE_STOPPED);
    }

    private void setNewState(@PlaybackStateCompat.State int state) {
        if (listener != null) {
            currentState = state;
            final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
            stateBuilder.setActions(getAvailableActions());
            stateBuilder.setState(currentState, 0, 1.0f, SystemClock.elapsedRealtime());
            listener.onPlaybackStateChange(stateBuilder.build());
        }
    }

    @PlaybackStateCompat.Actions
    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_REWIND
                | PlaybackStateCompat.ACTION_FAST_FORWARD;
        switch (currentState) {
            case PlaybackStateCompat.STATE_STOPPED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE;
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                actions |= PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE;
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            default:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE;
        }
        return actions;
    }

    public void setTtsSpeechRate(float speechRate) {
        if (tts == null) {
            Log.w(TAG, "TTS is null, cannot set speech rate");
            return;
        }
        
        if (speechRate == 0) {
            try {
                int systemRate = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.TTS_DEFAULT_RATE);
                speechRate = systemRate / 100.0f;
            } catch (Settings.SettingNotFoundException e) {
                Log.e(TAG, "TTS default rate setting not found", e);
                speechRate = 1.0f;
            }
        }
        
        try {
            tts.setSpeechRate(speechRate);
            Log.d(TAG, "TTS speech rate set to: " + speechRate);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set TTS speech rate", e);
        }
    }

    public void setWebViewCallback(WebViewListener listener) {
        this.webViewCallback = listener;
    }

    public WebViewListener getWebViewCallback() {
        return webViewCallback;
    }

    public void showFakeLoading() {
        if (webViewCallback != null) {
            webViewCallback.showFakeLoading();
        }
    }

    public void hideFakeLoading() {
        if (webViewCallback != null) {
            webViewCallback.hideFakeLoading();
        }
    }

    public boolean ttsIsNull() {
        return tts == null;
    }

    public boolean isWebViewConnected() {
        return webViewConnected;
    }

    public void setWebViewConnected(boolean isConnected) {
        this.webViewConnected = isConnected;
    }

    public boolean isUiControlPlayback() {
        return uiControlPlayback;
    }

    public void setUiControlPlayback(boolean isUiControlPlayback) {
        this.uiControlPlayback = isUiControlPlayback;
    }

    public long getCurrentId() {
        return currentId;
    }

    public boolean isPausedManually() {
        return isPausedManually;
    }

    public void setPausedManually(boolean isPaused) {
        sharedPreferencesRepository.setIsPausedManually(isPaused);
        isPausedManually = isPaused;
    }

    public boolean isPreparing() {
        return isPreparing;
    }

    public boolean isSpeaking() {
        return isPlaying();
    }

    public int getCurrentExtractProgress() {
        return currentExtractProgress;
    }
    
    /**
     * Check if TTS engine is healthy and bound
     */
    public boolean isTtsHealthy() {
        if (tts == null || !isInit) {
            return false;
        }
        try {
            String engine = tts.getDefaultEngine();
            return engine != null;
        } catch (Exception e) {
            Log.e(TAG, "TTS health check failed", e);
            isInit = false;
            return false;
        }
    }

    public boolean isSameArticleState(long entryId, String targetLanguage) {
        boolean sameId = currentId == entryId;
        boolean sameLang = (language == null && targetLanguage == null) || (language != null && language.equals(targetLanguage));
        boolean hasContent = (sentences != null && !sentences.isEmpty());
        return sameId && sameLang && (isSettingUpNewArticle || hasContent);
    }
}
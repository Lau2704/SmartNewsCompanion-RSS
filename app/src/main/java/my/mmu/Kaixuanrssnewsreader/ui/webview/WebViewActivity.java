package my.mmu.Kaixuanrssnewsreader.ui.webview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.model.EntryInfo;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsExtractor;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsPlayer;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsPlaylist;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsService;
import my.mmu.Kaixuanrssnewsreader.databinding.ActivityWebviewBinding;
import my.mmu.Kaixuanrssnewsreader.service.util.TextUtil;
import my.mmu.Kaixuanrssnewsreader.ui.feed.ReloadDialog;
import my.mmu.Kaixuanrssnewsreader.data.entry.Entry;
import my.mmu.Kaixuanrssnewsreader.data.entry.EntryRepository;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;



import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class WebViewActivity extends AppCompatActivity implements WebViewListener {
    private final static String TAG = "WebViewActivity";
    private LiveData<Entry> autoTranslationObserver;
    private Observer<Entry> checkAutoTranslated;
    // Share
    private ActivityWebviewBinding binding;
    private WebViewViewModel webViewViewModel;
    private WebView webView;
    private LinearProgressIndicator loading;
    private MenuItem browserButton;
    private MenuItem offlineButton;
    private MenuItem reloadButton;
    private MenuItem bookmarkButton;
    private MenuItem translationButton;
    private MenuItem highlightTextButton;
    private MenuItem backgroundMusicButton;
    private String currentLink;
    private long currentId;
    private long feedId;
    private String html;
    private String content;
    private String bookmark;
    private boolean isPlaying;
    private boolean isReadingMode;
    private boolean showOfflineButton;
    private boolean clearHistory;
    private MenuItem toggleTranslationButton;
    private boolean isTranslatedView = true;
    private MaterialToolbar toolbar;
    private FloatingActionButton autoSummaryButton;

    private String originalHtmlForSummary;
    private String summaryHtml;
    private boolean isSummaryView = false;
    private boolean hasGeneratedSummary = false;

    // JavaScript failure detection
    private int pageLoadRetryCount = 0;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private Handler retryHandler = new Handler(Looper.getMainLooper());
    private String currentLoadingUrl = null;

    // Translation
    private String targetLanguage;
    private String translationMethod;

    @Inject
    TextUtil textUtil;

    private CompositeDisposable compositeDisposable;
    private LiveData<Entry> liveEntryObserver;

    // Reading Mode
    private MenuItem switchPlayModeButton;
    private LinearLayout functionButtonsReadingMode;

    // Playing Mode
    private MenuItem switchReadModeButton;
    private MaterialButton playPauseButton;
    private MaterialButton skipNextButton;
    private MaterialButton skipPreviousButton;
    private MaterialButton fastForwardButton;
    private MaterialButton rewindButton;
    private LinearLayout functionButtons;
    private MediaBrowserHelper mMediaBrowserHelper;
    private Set<Long> translatedArticleIds = new HashSet<>();

    @Inject
    TtsPlayer ttsPlayer;

    @Inject
    TtsPlaylist ttsPlaylist;

    @Inject
    TtsExtractor ttsExtractor;

    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;

    @Inject
    EntryRepository entryRepository;

    @Inject
    my.mmu.Kaixuanrssnewsreader.data.playlist.PlaylistRepository playlistRepository;

    @Inject
    my.mmu.Kaixuanrssnewsreader.service.util.AutoTranslator autoTranslator;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (webView.canGoBack()) {
                    browserButton.setVisible(true);
                    webView.goBack();
                } else {
                    finish();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private final MediaControllerCompat.Callback mediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
                    super.onPlaybackStateChanged(state);
                    isPlaying = state.getState() == PlaybackStateCompat.STATE_PLAYING;
                    updatePlayPauseButtonIcon(isPlaying);
                    Log.d(TAG, "Playback state changed: " + state.getState());
                }
            };

    private void showTranslationLanguageDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Default Translation Language");

        CharSequence[] entries = getResources().getStringArray(R.array.defaultTranslationLanguage);
        CharSequence[] entryValues = getResources().getStringArray(R.array.defaultTranslationLanguage_values);

        builder.setItems(entries, (dialog, which) -> {
            makeSnackbar("Translating to " + entries[which]);
            String selectedValue = entryValues[which].toString();
            sharedPreferencesRepository.setDefaultTranslationLanguage(selectedValue);
            targetLanguage = selectedValue;
            translate();
            dialog.dismiss();
        });

        builder.show();
    }

    private void doWhenTranslationFinish(EntryInfo entryInfo, String originalHtml, String translatedHtml) {
        loading.setVisibility(View.INVISIBLE);

        if (entryInfo == null) {
            Log.e(TAG, "EntryInfo is null in doWhenTranslationFinish");
            loading.setVisibility(View.GONE);
            makeSnackbar("Error: Entry information not found");
            return;
        }

        if (translatedHtml == null || translatedHtml.trim().isEmpty()) {
            Log.e(TAG, "Translated HTML is null or empty");
            loading.setVisibility(View.GONE);
            makeSnackbar("Translation returned empty result");
            return;
        }

        Log.d(TAG, "Translated HTML length: " + translatedHtml.length());
        Log.d(TAG, "Translated HTML (first 500 chars): " + translatedHtml.substring(0, Math.min(500, translatedHtml.length())));

        compositeDisposable.add(
                Single.fromCallable(() -> {
                    if (webViewViewModel.getOriginalHtmlById(currentId) == null && originalHtml != null) {
                        webViewViewModel.updateOriginalHtml(originalHtml, currentId);
                        entryRepository.updateOriginalHtml(originalHtml, currentId);
                        Log.d(TAG, "Original HTML backed up from method parameter.");
                    }

                    Document doc = Jsoup.parse(translatedHtml);
                    doc.head().append(webViewViewModel.getStyle());

                    String finalHtml = doc.html();

                    webViewViewModel.updateHtml(finalHtml, currentId);
                    entryRepository.updateHtml(finalHtml, currentId);

                    String translatedContent = textUtil.extractHtmlContent(finalHtml, "--####--");
                    webViewViewModel.updateTranslated(translatedContent, currentId);
                    webViewViewModel.updateEntryTranslatedField(currentId, translatedContent);
                    entryRepository.updateTranslatedText(translatedContent, currentId);

                    return finalHtml;
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                finalHtml -> {
                                    webView.loadDataWithBaseURL("file///android_res/", finalHtml, "text/html", "UTF-8", null);

                                    translationButton.setVisible(false);
                                    toggleTranslationButton.setVisible(true);
                                    isTranslatedView = true;
                                    sharedPreferencesRepository.setIsTranslatedView(currentId, true);

                                    String translatedContent = textUtil.extractHtmlContent(finalHtml, "--####--");
                                    webViewViewModel.setTranslatedTextReady(currentId, translatedContent);

                                    Log.d(TAG, "FINAL translatedContent passed to TTS: " + translatedContent);
                                    Log.d(TAG, "FINAL currentId: " + currentId + ", isTranslatedView: " + isTranslatedView);

                                    if (sharedPreferencesRepository.getAutoTranslate()) {
                                        Log.d(TAG, "doWhenTranslationFinish: Auto-translate enabled, pre-translating previous and next articles");
                                        new Thread(() -> {
                                            List<Long> entryIds = playlistRepository.getPreviousAndNextEntryIds(currentId);
                                            for (Long id : entryIds) {
                                                Entry e = entryRepository.getEntryById(id);
                                                if (e != null) {
                                                    String entryHtml = e.getHtml() != null ? e.getHtml() : "";
                                                    if (!entryHtml.contains("translated-title")) {
                                                        autoTranslator.runAutoTranslationForEntry(id, entryHtml, e.getContent(), e.getTitle(), null);
                                                    }
                                                }
                                            }
                                        }).start();
                                    }
                                },
                                throwable -> {
                                    Log.e(TAG, "Error in doWhenTranslationFinish", throwable);
                                    loading.setVisibility(View.GONE);
                                    makeSnackbar("Error displaying translated content: " + throwable.getMessage());
                                }
                        )
        );
    }

    private void translate() {
        String apiKey = sharedPreferencesRepository.getOpenRouterApiKey();
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here")) {
            makeSnackbar("Translation API key not configured. Please set a valid OpenRouter API key.");
            return;
        }

        String html = webViewViewModel.getHtmlById(currentId);
        if (html == null) {
            makeSnackbar("No content to translate.");
            return;
        }

        makeSnackbar("Translation in progress");
        loading.setVisibility(View.VISIBLE);
        loading.setProgress(0);

        EntryInfo entryInfo = webViewViewModel.getEntryInfoById(currentId);
        String title = (entryInfo != null) ? entryInfo.getEntryTitle() : "";
        String feedLanguage = (entryInfo != null) ? entryInfo.getFeedLanguage() : null;

        if (targetLanguage == null || targetLanguage.isEmpty()) {
            targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();
        }
        if (targetLanguage == null || targetLanguage.isEmpty()) {
            targetLanguage = java.util.Locale.getDefault().getLanguage();
        }

        compositeDisposable.add(
            Single.fromCallable(() -> textUtil.extractHtmlContent(html, " "))
                .subscribeOn(Schedulers.io())
                .flatMap(plainText -> {
                    String sample = plainText.length() > 1000 ? plainText.substring(0, 1000) : plainText;
                    return textUtil.identifyLanguageRx(sample);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    identifiedLanguage -> {
                        String sourceLanguage = identifiedLanguage;
                        if (sourceLanguage == null || sourceLanguage.equals("und") || sourceLanguage.isEmpty()) {
                            sourceLanguage = feedLanguage;
                        }
                        if (sourceLanguage == null || sourceLanguage.isEmpty()) {
                            sourceLanguage = "en";
                        }

                        Log.d(TAG, "Translating from [" + sourceLanguage + "] to [" + targetLanguage + "]");
                        if (sourceLanguage.equals(targetLanguage)) {
                            loading.setVisibility(View.GONE);
                            makeSnackbar("Source and target languages are the same");
                            return;
                        }
                        performTranslation(sourceLanguage, targetLanguage, html, title);
                    },
                    error -> {
                        Log.e(TAG, "Language identification failed", error);
                        String sourceLanguage = feedLanguage != null ? feedLanguage : "en";
                        performTranslation(sourceLanguage, targetLanguage, html, title);
                    }
                )
        );
    }

    private String getSystemLanguage() {
        return java.util.Locale.getDefault().getLanguage();
    }

    private void performAutoSummary() {
        if (hasGeneratedSummary) {
            toggleSummaryView();
            return;
        }

        String apiKey = sharedPreferencesRepository.getOpenRouterApiKey();
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here")) {
            makeSnackbar("Translation API key not configured. Please set a valid OpenRouter API key.");
            return;
        }

        String content = webViewViewModel.getContentById(currentId);
        if (content == null || content.trim().isEmpty()) {
            makeSnackbar("No content to summarize.");
            return;
        }

        if (originalHtmlForSummary == null) {
            String currentHtml = webViewViewModel.getHtmlById(currentId);
            originalHtmlForSummary = currentHtml;
        }

        makeSnackbar("Generating summary...");
        loading.setVisibility(View.VISIBLE);
        loading.setProgress(0);

        compositeDisposable.add(
            textUtil.summarizeText(content)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    summary -> {
                        loading.setVisibility(View.GONE);
                        summaryHtml = formatSummaryAsHtml(summary);
                        hasGeneratedSummary = true;
                        sharedPreferencesRepository.setSummary(currentId, summary);
                        switchToSummaryView();
                    },
                    error -> {
                        Log.e(TAG, "Summarization failed", error);
                        loading.setVisibility(View.GONE);
                        String errorMsg = error != null && error.getMessage() != null
                                ? error.getMessage() : "Summarization failed. Please check your network connection and API key.";
                        makeSnackbar(errorMsg);
                    }
                )
        );
    }

    private void loadSavedSummaryOrGenerate() {
        String savedSummary = sharedPreferencesRepository.getSummary(currentId);
        if (savedSummary != null && !savedSummary.isEmpty()) {
            Log.d(TAG, "loadSavedSummaryOrGenerate: Found saved summary, restoring view");
            if (originalHtmlForSummary == null) {
                String currentHtml = webViewViewModel.getHtmlById(currentId);
                originalHtmlForSummary = currentHtml;
            }
            summaryHtml = formatSummaryAsHtml(savedSummary);
            hasGeneratedSummary = true;
            isSummaryView = sharedPreferencesRepository.getIsSummaryView(currentId);
            if (isSummaryView) {
                Log.d(TAG, "loadSavedSummaryOrGenerate: Switching to summary view");
                switchToSummaryView();
            } else {
                Log.d(TAG, "loadSavedSummaryOrGenerate: Staying on original view (summary available)");
            }
        } else if (sharedPreferencesRepository.getDisplaySummary()) {
            Log.d(TAG, "loadSavedSummaryOrGenerate: No saved summary, auto-generating because displaySummary is enabled");
            performAutoSummary();
        } else {
            Log.d(TAG, "loadSavedSummaryOrGenerate: No saved summary and auto-summary disabled, not generating");
        }
    }

    private String formatSummaryAsHtml(String summary) {
        EntryInfo entryInfo = webViewViewModel.getEntryInfoById(currentId);
        String htmlHeader = "";
        if (entryInfo != null) {
            htmlHeader = webViewViewModel.getHtml(
                    entryInfo.getEntryTitle(),
                    entryInfo.getFeedTitle(),
                    entryInfo.getEntryPublishedDate(),
                    entryInfo.getFeedImageUrl()
            );
        }
        String style = webViewViewModel.getStyle();
        return "<html><head>" + style + "</head><body>" + htmlHeader + 
               "<div class=\"article-summary\">" + 
               "<h2>Summary</h2>" + 
               "<p>" + summary.replace("\n", "<br>") + "</p>" + 
               "</div></body></html>";
    }

    private void switchToSummaryView() {
        if (summaryHtml != null) {
            webView.loadDataWithBaseURL("file///android_res/", summaryHtml, "text/html", "UTF-8", null);
            isSummaryView = true;
            sharedPreferencesRepository.setIsSummaryView(currentId, true);
            autoSummaryButton.setImageResource(R.drawable.auto_summary_no_background);
            makeSnackbar("Showing summary view");
        }
    }

    private void switchToOriginalView() {
        if (originalHtmlForSummary != null) {
            webView.loadDataWithBaseURL("file///android_res/", originalHtmlForSummary, "text/html", "UTF-8", null);
            isSummaryView = false;
            sharedPreferencesRepository.setIsSummaryView(currentId, false);
            autoSummaryButton.setImageResource(R.drawable.ic_newspaper);
            makeSnackbar("Showing original article");
        }
    }

    private void toggleSummaryView() {
        if (isSummaryView) {
            switchToOriginalView();
        } else {
            switchToSummaryView();
        }
    }

    private void performTranslation(String sourceLang, String targetLang, String html, String title) {
        Single<String> translationFlow;
        switch (translationMethod) {
            case "lineByLine":
                translationFlow = textUtil.translateHtmlLineByLine(sourceLang, targetLang, html, title, currentId, this::updateLoadingProgress);
                break;
            case "paragraphByParagraph":
                translationFlow = textUtil.translateHtmlByParagraph(sourceLang, targetLang, html, title, currentId, this::updateLoadingProgress);
                break;
            default:
                translationFlow = textUtil.translateHtmlAllAtOnce(sourceLang, targetLang, html, title, currentId, this::updateLoadingProgress);
        }

        final String originalHtml = html;

        compositeDisposable.add(
            translationFlow
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        translatedHtml -> {
                            Log.d(TAG, "Translation completed");
                            doWhenTranslationFinish(webViewViewModel.getLastVisitedEntry(), originalHtml, translatedHtml);
                        },
                        throwable -> {
                            Log.e(TAG, "Translation failed", throwable);
                            loading.setVisibility(View.GONE);
                            String errorMsg = throwable != null && throwable.getMessage() != null
                                    ? throwable.getMessage() : "Translation failed. Please check your network connection and API key.";
                            makeSnackbar(errorMsg);
                        }
                )
        );
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        webViewViewModel = new ViewModelProvider(this).get(WebViewViewModel.class);

        webViewViewModel.getTranslatedTextReady().observe(this, translatedText -> {
            if (!isReadingMode && isTranslatedView && translatedText != null && !translatedText.trim().isEmpty()) {
                Log.d(TAG, "TTS triggered after LiveData translation update");

                String lang = getLanguageForCurrentView(currentId, isTranslatedView, "en");

                ttsPlayer.extract(currentId, feedId, translatedText, lang);
                Log.d(TAG, "LiveData.observe fired, isTranslatedView = " + isTranslatedView);
            }
        });

        isReadingMode = getIntent().getBooleanExtra("read", false);

        if (ttsPlayer.isPlaying() && isReadingMode) {
            ttsPlayer.stop();
        }

        initializeUI();

        targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();
        translationMethod = sharedPreferencesRepository.getTranslationMethod();
        compositeDisposable = new CompositeDisposable();

        Log.d(TAG, "Target language from settings: " + targetLanguage);
        Log.d(TAG, "Translation method: " + translationMethod);
        Log.d(TAG, "OpenRouter API key configured: " + (sharedPreferencesRepository.getOpenRouterApiKey() != null && !sharedPreferencesRepository.getOpenRouterApiKey().isEmpty()));

        initializeToolbarListeners();
        initializeWebViewSettings();
        initializePlaybackModes();
        loadEntryContent();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (event.getAction() == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                        keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                        keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                        keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                        keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {

            MediaSessionCompat mediaSession = TtsService.getMediaSession();
            if (mediaSession != null && mediaSession.isActive()) {
                MediaControllerCompat controller = mediaSession.getController();
                controller.dispatchMediaButtonEvent(event);
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void initializeUI() {
        binding = ActivityWebviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        webView = binding.webview;
        loading = binding.loadingWebView;
        functionButtons = binding.functionButtons;
        functionButtonsReadingMode = binding.functionButtonsReading;

        playPauseButton = binding.playPauseButton;
        skipNextButton = binding.skipNextButton;
        skipPreviousButton = binding.skipPreviousButton;
        fastForwardButton = binding.fastForwardButton;
        rewindButton = binding.rewindButton;

        toolbar = binding.toolbar;
        browserButton = toolbar.getMenu().findItem(R.id.openInBrowser);
        offlineButton = toolbar.getMenu().findItem(R.id.exitBrowser);
        reloadButton = toolbar.getMenu().findItem(R.id.reload);
        bookmarkButton = toolbar.getMenu().findItem(R.id.bookmark);
        translationButton = toolbar.getMenu().findItem(R.id.translate);
        toggleTranslationButton = toolbar.getMenu().findItem(R.id.toggleTranslation);
        highlightTextButton = toolbar.getMenu().findItem(R.id.highlightText);
        backgroundMusicButton = toolbar.getMenu().findItem(R.id.toggleBackgroundMusic);
        switchReadModeButton = toolbar.getMenu().findItem(R.id.switchReadMode);
        switchPlayModeButton = toolbar.getMenu().findItem(R.id.switchPlayMode);

        toggleTranslationButton.setVisible(false);

        highlightTextButton.setTitle(sharedPreferencesRepository.getHighlightText()
                ? R.string.highlight_text_turn_off : R.string.highlight_text_turn_on);
        backgroundMusicButton.setTitle(sharedPreferencesRepository.getBackgroundMusic()
                ? R.string.background_music_turn_off : R.string.background_music_turn_on);

        autoSummaryButton = binding.autoSummaryButton;
        autoSummaryButton.setOnClickListener(v -> performAutoSummary());
    }

    private void loadHtmlIntoWebView(String html) {
        if (html == null || html.trim().isEmpty()) {
            return;
        }

        compositeDisposable.add(
                Single.fromCallable(() -> {
                    EntryInfo entryInfo = webViewViewModel.getEntryInfoById(currentId);
                    Document doc = Jsoup.parse(html);
                    doc.head().append(webViewViewModel.getStyle());

                    if (entryInfo != null && !doc.html().contains("class=\"entry-header\"")) {
                        doc.selectFirst("body").prepend(
                                webViewViewModel.getHtml(
                                        entryInfo.getEntryTitle(),
                                        entryInfo.getFeedTitle(),
                                        entryInfo.getEntryPublishedDate(),
                                        entryInfo.getFeedImageUrl()
                                )
                        );
                    }

                    return doc.html();
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                processedHtml -> {
                                    webView.loadDataWithBaseURL("file///android_res/", processedHtml, "text/html", "UTF-8", null);

                                    webView.postDelayed(() -> {
                                        int scrollX = sharedPreferencesRepository.getScrollX(currentId);
                                        int scrollY = sharedPreferencesRepository.getScrollY(currentId);
                                        webView.scrollTo(scrollX, scrollY);
                                    }, 300);

                                    syncLoadingWithTts();
                                },
                                throwable -> {
                                    Log.e(TAG, "Error loading HTML into WebView", throwable);
                                }
                        )
        );
    }

    private void updateToggleTranslationVisibility() {
        String originalHtml = webViewViewModel.getOriginalHtmlById(currentId);
        String translatedHtml = webViewViewModel.getHtmlById(currentId);

        boolean hasTranslation = originalHtml != null && translatedHtml != null && !originalHtml.equals(translatedHtml);

        if (hasTranslation) {
            translationButton.setVisible(false);
            toggleTranslationButton.setVisible(true);
        } else {
            translationButton.setVisible(true);
            toggleTranslationButton.setVisible(false);
        }

        Log.d(TAG, "ToggleTranslationButton visibility set to: " + hasTranslation);
    }

    private void initializePlaybackModes() {
        if (isReadingMode) {
            switchReadMode();
        } else {
            switchPlayMode();
        }
    }

        private void loadEntryContent() {

            long intentId = getIntent().getLongExtra("entry_id", -1);

            if (intentId != currentId) {
                originalHtmlForSummary = null;
                summaryHtml = null;
                isSummaryView = false;
                hasGeneratedSummary = false;
                autoSummaryButton.setImageResource(R.drawable.auto_summary_no_background);
            }

            Log.d(TAG, "Loading article with Intent ID: " + intentId);

            if (ttsPlayer.isPlaying()) {
                ttsPlayer.stop();
                Log.d(TAG, "TTS stopped before loading new article");

            }

    

            compositeDisposable.add(

                    Single.fromCallable(() -> {
                        EntryInfo info = null;
                        if (intentId != -1) {
                            info = webViewViewModel.getEntryInfoById(intentId);
                        }

                        if (info == null) {
                            info = webViewViewModel.getLastVisitedEntry();
                        }

                        return info;

                    })

                    .flatMap(entryInfo -> {

                        if (entryInfo == null) {

                            return Single.error(new Exception("No article found"));

                        }

                        // Fetch the full entry content
                        Entry entry = entryRepository.getEntryById(entryInfo.getEntryId());
                        return Single.just(new androidx.core.util.Pair<>(entryInfo, entry));
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(pair -> {

                        EntryInfo entryInfo = pair.first;
                        Entry entry = pair.second;

                        if (entry == null) {
                            makeSnackbar("Failed to load article content.");
                            return;
                        }

                        currentId = entryInfo.getEntryId();
                        feedId = entryInfo.getFeedId();

                        // Update playlist so metadata calls are correct
                        ttsPlaylist.updatePlayingId(currentId);
                        loadEntryContentWithData(entry, entryInfo);

                    }, throwable -> {
                        Log.e(TAG, "Error loading entry", throwable);
                        makeSnackbar("Failed to load article.");

                    })

            );

        }

    

                private void loadEntryContentWithData(Entry entry, EntryInfo entryInfo) {
                    if (sharedPreferencesRepository.getWebViewMode(currentId)) {
                        loadFromBrowserMode(entryInfo);
                        return;
                    }
                    cachedEntryInfo = entryInfo;
                    targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();

                    boolean hasTranslation = entry.getOriginalHtml() != null && entry.getHtml() != null && !entry.getOriginalHtml().equals(entry.getHtml());

                    if (hasTranslation) {
                        toggleTranslationButton.setVisible(true);
                        translationButton.setVisible(false);
                        webViewViewModel.updateOriginalHtml(entry.getOriginalHtml(), entry.getId());
                        if (entry.getHtml() != null) {
                            webViewViewModel.updateHtml(entry.getHtml(), entry.getId());
                        }
                    } else {
                        toggleTranslationButton.setVisible(false);
                        translationButton.setVisible(true);
                    }
                    isTranslatedView = false;

                    if (entry.getTranslated() != null && entry.getHtml() != null) {
                        isTranslatedView = true;
                        Log.d(TAG, "loadEntryContent: Article has translated HTML available, setting isTranslatedView=true");
                    } else if (sharedPreferencesRepository.hasTranslationToggle(currentId)) {
                        isTranslatedView = sharedPreferencesRepository.getIsTranslatedView(currentId);
                        Log.d(TAG, "loadEntryContent: Using saved preference isTranslatedView=" + isTranslatedView);
                    }else {
                        Log.d(TAG, "loadEntryContent: Article not translated, isTranslatedView=false");
                    }
                    Log.d(TAG, "loadEntryContent: FINAL isTranslatedView = " + isTranslatedView);

                    if (!hasTranslation && sharedPreferencesRepository.getAutoTranslate()) {
                        Log.d(TAG, "loadEntryContent: Auto-translate enabled, triggering translation");
                        translate();
                    }

                    loadSavedSummaryOrGenerate();

                    String html = isTranslatedView ? entry.getHtml() : entry.getOriginalHtml();

        Log.d("LoadEntry", "htmlToLoad (translated) = " + (html != null ? html.length() : "null"));

        String contentToRead = isTranslatedView
                ? entry.getTranslated()
                : entry.getContent();

        String lang = getLanguageForCurrentView(currentId, isTranslatedView, "en");

        Log.d(TAG, "loadEntryContent - About to speak " + (isTranslatedView ? "Translated" : "Original"));
        Log.d(TAG, "Language to use: " + lang);
        Log.d(TAG, "Text to read length: " + (contentToRead != null ? contentToRead.length() : 0));

        Log.d(TAG, "Calling setCurrentLanguage with: " + lang + ", lock=true");
        ttsExtractor.setCurrentLanguage(lang, true);

        if (!isSummaryView && html != null && !html.trim().isEmpty()) {
            loadHtmlIntoWebView(html);

            if (!ttsPlayer.isSameArticleState(entry.getId(), lang)) {
                compositeDisposable.add(
                        Completable.fromAction(() -> {
                            ttsPlayer.extract(entry.getId(), entry.getFeedId(), contentToRead, lang);
                        })
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        () -> {
                                            syncLoadingWithTts();
                                        },
                                        throwable -> {
                                            Log.e(TAG, "Error extracting TTS", throwable);
                                            syncLoadingWithTts();
                                        }
                                )
                );
            } else {
                Log.d(TAG, "TTS is already prepared for this article/language. Skipping re-extraction.");
                syncLoadingWithTts();
            }
        } else {
            Log.w(TAG, "HTML missing, skipping load.");
            // If HTML is missing, we might still want to try TTS extract from the raw link?
            // But usually extractAllEntries handles it.
            // If we are here, it means DB has entry but no HTML.
            // Maybe trigger extraction?
            if (isReadingMode) {
                 // In reading mode, TtsExtractor callback is set by WebClient.
                 // But we need to load URL to trigger it.
                 // If html is null, loadUrl fallback?
                 Log.d(TAG, "Loading URL directly as HTML is missing.");
                 webView.loadUrl(entryInfo.getEntryLink());
            }
        }

                    sharedPreferencesRepository.setCurrentReadingEntryId(currentId);

        observeLiveEntry();
        observeAutoTranslation();

        syncLoadingWithTts();
    }

    private void loadFromBrowserMode(EntryInfo entryInfo) {
        browserButton.setVisible(false);
        offlineButton.setVisible(true);
        webView.loadUrl(entryInfo.getEntryLink());
    }

            private void observeLiveEntry() {
                webViewViewModel.triggerEntryRefresh(currentId);
                webViewViewModel.getLiveEntry().observe(this, entry -> {
                    if (entry == null) {
                        toggleTranslationButton.setVisible(false);
                        makeSnackbar("This article is missing.");
                        return;
                    }
                    String dbOriginal = entry.getOriginalHtml();
                    String dbHtml = entry.getHtml();
                    String vmOriginal = webViewViewModel.getOriginalHtmlLiveData().getValue();
                    String vmHtml = webViewViewModel.getTranslatedHtmlLiveData().getValue();

                    if (dbOriginal != null && !dbOriginal.equals(vmOriginal)) {
                        webViewViewModel.setOriginalHtml(dbOriginal);

                    }
                    if (dbHtml != null && !dbHtml.equals(vmHtml)) {
                        webViewViewModel.setHtml(dbHtml);
                    }
                });

                webViewViewModel.getOriginalHtmlLiveData().observe(this, originalHtml -> {
                    updateToggleStateAndWebView(originalHtml, webViewViewModel.getTranslatedHtmlLiveData().getValue());
                });
                webViewViewModel.getTranslatedHtmlLiveData().observe(this, translatedHtml -> {
                    updateToggleStateAndWebView(webViewViewModel.getOriginalHtmlLiveData().getValue(), translatedHtml);
                });

    

            }

    private void updateToggleStateAndWebView(String originalHtml, String translatedHtml) {
        boolean hasOriginal = originalHtml != null && !originalHtml.trim().isEmpty();
        boolean hasTranslated = translatedHtml != null && !translatedHtml.trim().isEmpty();

        toggleTranslationButton.setVisible(hasOriginal && hasTranslated);
        toggleTranslationButton.setTitle(isTranslatedView ? "Show Original" : "Show Translation");

        String htmlToLoad = isTranslatedView ? translatedHtml : originalHtml;

        Log.d(TAG, "LiveEntry - Current Mode: " + (isTranslatedView ? "Translated" : "Original"));
        Log.d(TAG, "LiveEntry - HTML to Load:\n" + (htmlToLoad != null ? htmlToLoad.length() + " chars" : "null"));

        if (htmlToLoad != null && !htmlToLoad.trim().isEmpty()) {
            loadHtmlToWebView(htmlToLoad);
        } else {
            Log.w(TAG, "Skipped loading empty html in updateToggleStateAndWebView()");
        }
    }

    private void observeAutoTranslation() {
        LiveData<Entry> entryLiveData = webViewViewModel.getEntryEntityById(currentId);
        final Observer<Entry>[] observerHolder = new Observer[1];
        observerHolder[0] = new Observer<Entry>() {
            @Override
            public void onChanged(Entry entry) {
                if (entry != null && entry.getTranslated() != null) {
                    compositeDisposable.add(
                            Single.fromCallable(() -> {
                                String originalHtmlFromDb = entryRepository.getOriginalHtmlById(currentId);
                                String translatedHtmlFromDb = entry.getHtml();

                                if (originalHtmlFromDb != null) {
                                    webViewViewModel.updateOriginalHtml(originalHtmlFromDb, currentId);
                                    Log.d(TAG, "Original HTML restored from DB.");
                                }

                                if (translatedHtmlFromDb != null) {
                                    webViewViewModel.updateHtml(translatedHtmlFromDb, currentId);
                                    Log.d(TAG, "Translated HTML synced from auto translation.");
                                }

                                toggleTranslationButton.setTitle(isTranslatedView ? "Show Original" : "Show Translation");

                                Log.d(TAG, "AutoTranslation - Final Original:\n" + webViewViewModel.getOriginalHtmlById(currentId));
                                Log.d(TAG, "AutoTranslation - Final Translated:\n" + webViewViewModel.getHtmlById(currentId));

                                webViewViewModel.triggerEntryRefresh(currentId);
                                return null;
                            })
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            result -> {
                                                entryLiveData.removeObserver(observerHolder[0]);
                                            },
                                            throwable -> {
                                                Log.e(TAG, "Error in observeAutoTranslation", throwable);
                                                entryLiveData.removeObserver(observerHolder[0]);
                                            }
                                    )
                    );
                }
            }
        };
        entryLiveData.observeForever(observerHolder[0]);
    }

    private void loadHtmlToWebView(String html) {
        if (html == null || html.trim().isEmpty()) {
            return;
        }

        compositeDisposable.add(
                Single.fromCallable(() -> {
                    EntryInfo entryInfo = webViewViewModel.getEntryInfoById(currentId);
                    Document doc = Jsoup.parse(html);
                    doc.head().append(webViewViewModel.getStyle());

                    if (entryInfo != null && !doc.html().contains("class=\"entry-header\"")) {
                        doc.selectFirst("body").prepend(
                                webViewViewModel.getHtml(
                                        entryInfo.getEntryTitle(),
                                        entryInfo.getFeedTitle(),
                                        entryInfo.getEntryPublishedDate(),
                                        entryInfo.getFeedImageUrl()
                                )
                        );
                    }

                    return doc.html();
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                processedHtml -> {
                                    webView.loadDataWithBaseURL("file///android_res/", processedHtml, "text/html", "UTF-8", null);
                                },
                                throwable -> {
                                    Log.e(TAG, "Error loading HTML to WebView", throwable);
                                }
                        )
        );
    }

    private boolean handleOtherToolbarItems(int itemId) {
        switch (itemId) {
            case R.id.translate:
                translate();
                return true;

            case R.id.zoomIn:
                adjustTextZoom(true);
                return true;

            case R.id.zoomOut:
                adjustTextZoom(false);
                return true;

            case R.id.bookmark:
                toggleBookmark();
                return true;

            case R.id.share:
                shareCurrentLink();
                return true;

            case R.id.openInBrowser:
                browserButton.setVisible(false);
                offlineButton.setVisible(true);
                sharedPreferencesRepository.setWebViewMode(currentId, true);
                webView.loadUrl(currentLink);
                hideFakeLoading();
                return true;

            case R.id.exitBrowser:
                sharedPreferencesRepository.setWebViewMode(currentId, false);
                EntryInfo entryInfo = webViewViewModel.getLastVisitedEntry();
                rebuildHtml(entryInfo, html -> {
                    loadEntryContent();
                    offlineButton.setVisible(false);
                    browserButton.setVisible(true);
                    hideFakeLoading();
                });
                return true;

            case R.id.reload:
                ReloadDialog dialog = new ReloadDialog(this, feedId, R.string.reload_confirmation, R.string.reload_message);
                dialog.show(getSupportFragmentManager(), ReloadDialog.TAG);
                return true;

            case R.id.toggleBackgroundMusic:
                toggleBackgroundMusic();
                return true;

            case R.id.openTtsSetting:
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                return true;

            case R.id.toggleTranslation:
                boolean currentMode = sharedPreferencesRepository.getIsTranslatedView(currentId);
                isTranslatedView = !currentMode;
                sharedPreferencesRepository.setIsTranslatedView(currentId, isTranslatedView);

                Entry entry = webViewViewModel.getEntryById(currentId);
                if (entry == null) {
                    makeSnackbar("Entry not found.");
                    return true;
                }

                entryInfo = webViewViewModel.getEntryInfoById(currentId);
                if (entryInfo == null) {
                    makeSnackbar("Feed language info not found.");
                    isTranslatedView = currentMode;
                    sharedPreferencesRepository.setIsTranslatedView(currentId, currentMode);
                    return true;
                }

                String translatedHtml = webViewViewModel.getHtmlById(currentId);
                String originalHtml = webViewViewModel.getOriginalHtmlById(currentId);
                String htmlToLoad = isTranslatedView ? translatedHtml : originalHtml;

                Log.d(TAG, "TOGGLE BUTTON PRESSED");
                Log.d(TAG, "Original HTML:\n" + originalHtml);
                Log.d(TAG, "Translated HTML:\n" + translatedHtml);
                Log.d(TAG, "HTML loaded for toggle view:\n" + htmlToLoad);

                if (htmlToLoad != null && !htmlToLoad.trim().isEmpty()) {
                    toggleTranslationButton.setTitle(isTranslatedView ? "Show Original" : "Show Translation");
                    loadHtmlIntoWebView(htmlToLoad);

                    if (isTranslatedView) {
                        String translated = entry.getTranslated();
                        if (translated != null && !translated.trim().isEmpty()) {
                            Log.d(TAG, "ToggleTranslation: Broadcasting translatedTextReady again");
                            webViewViewModel.setTranslatedTextReady(currentId, translated);
                            String lang = getLanguageForCurrentView(currentId, isTranslatedView, "en");
                            ttsPlayer.extract(entry.getId(), entry.getFeedId(), translated, lang);
                        } else {
                            Log.w(TAG, "ToggleTranslation: translated content missing, skipping extract");
                        }
                    } else {
                        String original = entry.getContent();
                        String lang = getLanguageForCurrentView(currentId, isTranslatedView, "en");
                        if (original != null && !original.trim().isEmpty()) {
                            Log.d(TAG, "ToggleTranslation: Reading original content");
                            ttsPlayer.extract(entry.getId(), entry.getFeedId(), original, lang);
                        }
                    }
                } else {
                    makeSnackbar("No alternate version available.");
                    isTranslatedView = currentMode;
                    sharedPreferencesRepository.setIsTranslatedView(currentId, currentMode);
                }
                return true;

            default:
                return false;
        }
    }

    private void rebuildHtml(EntryInfo entryInfo, WebViewRebuildCallback callback) {
        compositeDisposable.add(
                Single.fromCallable(() -> {
                    String html = webViewViewModel.getHtmlById(entryInfo.getEntryId());

                    Document doc = Jsoup.parse(html);
                    doc.head().append(webViewViewModel.getStyle());

                    Objects.requireNonNull(doc.selectFirst("body")).prepend(
                            webViewViewModel.getHtml(
                                    entryInfo.getEntryTitle(),
                                    entryInfo.getFeedTitle(),
                                    entryInfo.getEntryPublishedDate(),
                                    entryInfo.getFeedImageUrl()
                            )
                    );

                    return doc.html();
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                callback::onRebuildComplete,
                                throwable -> {
                                    Log.e(TAG, "Error rebuilding HTML", throwable);
                                    makeSnackbar("Error rebuilding content");
                                }
                        )
        );
    }
    private interface WebViewRebuildCallback {
        void onRebuildComplete(String html);
    }

    private void adjustTextZoom(boolean zoomIn) {
        int currentZoom = webView.getSettings().getTextZoom();
        int newZoom = zoomIn ? currentZoom + 10 : currentZoom - 10;
        webView.getSettings().setTextZoom(newZoom);
        sharedPreferencesRepository.setTextZoom(newZoom);
    }

    private void toggleBookmark() {
        if (bookmark == null || bookmark.equals("N")) {
            bookmarkButton.setIcon(R.drawable.ic_bookmark_filled);
            webViewViewModel.updateBookmark("Y", currentId);
            bookmark = "Y";
            makeSnackbar("Bookmark Complete");
        } else {
            bookmarkButton.setIcon(R.drawable.ic_bookmark_outline);
            webViewViewModel.updateBookmark("N", currentId);
            bookmark = "N";
            makeSnackbar("Bookmark Removed");
        }
    }

    private void shareCurrentLink() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, currentLink);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
    }

    private void toggleBackgroundMusic() {
        boolean backgroundMusic = sharedPreferencesRepository.getBackgroundMusic();
        sharedPreferencesRepository.setBackgroundMusic(!backgroundMusic);
        if (backgroundMusic) {
            ttsPlayer.stopMediaPlayer();
            backgroundMusicButton.setTitle(R.string.background_music_turn_on);
            makeSnackbar("Background music is turned off");
        } else {
            ttsPlayer.setupMediaPlayer(false);
            backgroundMusicButton.setTitle(R.string.background_music_turn_off);
            makeSnackbar("Background music is turned on");
        }
    }

    private void initializeToolbarListeners() {
        toolbar.setNavigationOnClickListener(view -> onBackPressed());

        toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.switchPlayMode) {
                isReadingMode = false;
                functionButtonsReadingMode.setVisibility(View.INVISIBLE);
                switchPlayModeButton.setVisible(false);
                ttsExtractor.setCallback((WebViewListener) null);
                switchPlayMode();
                mMediaBrowserHelper.onStart();
                functionButtons.setVisibility(View.VISIBLE);
                functionButtons.setAlpha(1.0f);
                return true;

            } else if (itemId == R.id.switchReadMode) {
                isReadingMode = true;
                functionButtons.setVisibility(View.INVISIBLE);
                switchReadModeButton.setVisible(false);
                ttsPlayer.setWebViewCallback(null);
                mMediaBrowserHelper.getTransportControls().stop();
                mMediaBrowserHelper.onStop();
                webView.clearMatches();
                switchReadMode();
                return true;

            } else if (itemId == R.id.highlightText) {
                boolean isHighlight = sharedPreferencesRepository.getHighlightText();
                sharedPreferencesRepository.setHighlightText(!isHighlight);
                if (isHighlight) {
                    webView.clearMatches();
                    highlightTextButton.setTitle(R.string.highlight_text_turn_on);
                    Snackbar.make(findViewById(R.id.webView_view), "Highlight is turned off", Snackbar.LENGTH_SHORT).show();
                } else {
                    highlightTextButton.setTitle(R.string.highlight_text_turn_off);
                    Snackbar.make(findViewById(R.id.webView_view), "Highlight is turned on", Snackbar.LENGTH_SHORT).show();
                }
                return true;
            }

            return handleOtherToolbarItems(itemId);
        });
    }

    private void initializeWebViewSettings() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

        int textZoom = sharedPreferencesRepository.getTextZoom();
        if (textZoom != 0) {
            webView.getSettings().setTextZoom(textZoom);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean isNight = sharedPreferencesRepository.getNight();
            webView.getSettings().setForceDark(isNight
                    ? WebSettings.FORCE_DARK_ON
                    : WebSettings.FORCE_DARK_OFF);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);

                int ttsProgress = ttsPlayer.getCurrentExtractProgress();

                int combinedProgress = Math.min(newProgress, ttsProgress);

                loading.setVisibility(View.VISIBLE);
                loading.setProgress(combinedProgress);
                if (combinedProgress >= 95 && (!ttsPlayer.isPreparing() || ttsPlayer.ttsIsNull())) {
                    loading.setVisibility(View.GONE);
                }

                // Check for JavaScript execution after page loaded
                if (newProgress == 100 && currentLoadingUrl != null) {
                    checkJavaScriptExecution();
                }
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                // Detect JavaScript errors
                if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TAG, "JavaScript Error: " + consoleMessage.message() +
                            " at line " + consoleMessage.lineNumber() +
                            " of " + consoleMessage.sourceId());
                }
                return super.onConsoleMessage(consoleMessage);
            }
        });
    }

    @Override
    public void showFakeLoading() {
        runOnUiThread(() -> {
            Log.d(TAG, "TTS is preparing, showing fake loading indicator.");
            loading.setProgress(0);
            loading.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void hideFakeLoading() {
        runOnUiThread(() -> {
            Log.d(TAG, "TTS is ready, hiding fake loading indicator.");
            loading.setVisibility(View.GONE);
        });
    }

    @Override
    public void updateLoadingProgress(int progress) {
        runOnUiThread(() -> {
            if (loading.getVisibility() != View.VISIBLE) {
                loading.setVisibility(View.VISIBLE);
            }
            loading.setProgress(progress);

            if (progress >= 100 && !ttsPlayer.isPreparing()) {
                loading.setVisibility(View.GONE);
            }
        });
    }

    public void syncLoadingWithTts() {
        runOnUiThread(() -> {
            int ttsProgress = ttsPlayer.getCurrentExtractProgress();
            int webProgress = webView.getProgress();
            int combinedProgress = Math.min(ttsProgress, webProgress);

            if (combinedProgress >= 100 && !ttsPlayer.isPreparing()) {
                loading.setProgress(100);
                loading.setVisibility(View.GONE);
                Log.d(TAG, "[syncLoadingWithTts] Forcibly hid loading.");
            } else {
                loading.setProgress(combinedProgress);
                loading.setVisibility(View.VISIBLE);
                Log.d(TAG, "[syncLoadingWithTts] Still loading... progress = " + combinedProgress);
            }
        });
    }

    private void switchReadMode() {
        functionButtonsReadingMode.setVisibility(View.VISIBLE);

        webView.setWebViewClient(new ReadingWebClient());

        binding.nextArticleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ttsPlaylist.skipNext()) {
                    setupReadingWebView();
                } else {
                    Snackbar.make(findViewById(R.id.webView_view), "This is the last article", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        binding.previousArticleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ttsPlaylist.skipPrevious()) {
                    setupReadingWebView();
                } else {
                    Snackbar.make(findViewById(R.id.webView_view), "This is the first article", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        setupReadingWebView();

        ttsPlayer.setupMediaPlayer(false);

        switchPlayModeButton.setVisible(true);
    }

    private void switchPlayMode() {
        webView.setWebViewClient(new WebClient());
        setupMediaPlaybackButtons();

        mMediaBrowserHelper = new MediaBrowserConnection(this);
        mMediaBrowserHelper.registerCallback(new MediaBrowserListener());

        switchReadModeButton.setVisible(true);
    }

    private void setupMediaPlaybackButtons() {
        playPauseButton.setOnClickListener(view -> {
            Log.d(TAG, "Play/Pause button clicked - isPlaying: " + isPlaying);
            if (mMediaBrowserHelper == null) {
                Log.e(TAG, "MediaBrowserHelper is null!");
                makeSnackbar("Media service not connected");
                return;
            }
            
            if (mMediaBrowserHelper.getTransportControls() == null) {
                Log.e(TAG, "TransportControls is null!");
                makeSnackbar("Media controls not ready");
                return;
            }
            
            if (isPlaying) {
                mMediaBrowserHelper.getTransportControls().pause();
                Log.d(TAG, "switchPlayMode: pausing " + ttsPlaylist.getPlayingId());
            } else {
                mMediaBrowserHelper.getTransportControls().play();
                Log.d(TAG, "switchPlayMode: playing " + ttsPlaylist.getPlayingId());
            }
        });

        skipNextButton.setOnClickListener(view -> {
            if (mMediaBrowserHelper != null && mMediaBrowserHelper.getTransportControls() != null) {
                mMediaBrowserHelper.getTransportControls().skipToNext();
            }
        });
        
        skipPreviousButton.setOnClickListener(view -> {
            if (mMediaBrowserHelper != null && mMediaBrowserHelper.getTransportControls() != null) {
                mMediaBrowserHelper.getTransportControls().skipToPrevious();
            }
        });
        
        fastForwardButton.setOnClickListener(view -> {
            if (mMediaBrowserHelper != null && mMediaBrowserHelper.getTransportControls() != null) {
                mMediaBrowserHelper.getTransportControls().fastForward();
            }
        });
        
        rewindButton.setOnClickListener(view -> {
            if (mMediaBrowserHelper != null && mMediaBrowserHelper.getTransportControls() != null) {
                mMediaBrowserHelper.getTransportControls().rewind();
            }
        });
    }

    private void setupReadingWebView() {
        loading.setVisibility(View.VISIBLE);
        loading.setProgress(0);
        bookmarkButton.setVisible(false);
        loading.setProgress(0);
        translationButton.setVisible(false);
        showOfflineButton = false;

        MediaMetadataCompat metadata = ttsPlaylist.getCurrentMetadata();

        content = metadata.getString("content");
        bookmark = metadata.getString("bookmark");
        currentLink = metadata.getString("link");
        currentId = Long.parseLong(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID));
        updateToggleTranslationVisibility();
        feedId = metadata.getLong("feedId");

        if (bookmark == null || bookmark.equals("N")) {
            bookmarkButton.setIcon(R.drawable.ic_bookmark_outline);
        } else {
            bookmarkButton.setIcon(R.drawable.ic_bookmark_filled);
        }

        boolean isWebViewMode = sharedPreferencesRepository.getWebViewMode(currentId);

        if (isWebViewMode) {
            webView.loadUrl(currentLink);
            Log.d(TAG, "Restoring web view mode: " + currentLink);
            browserButton.setVisible(false);
            offlineButton.setVisible(true);
            showOfflineButton = false;
        } else {
            String entryTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE);
            String feedTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
            long date = metadata.getLong("date");
            Date publishDate = new Date(date);
            String feedImageUrl = metadata.getString("feedImageUrl");

            isTranslatedView = sharedPreferencesRepository.getIsTranslatedView(currentId);

            compositeDisposable.add(
                    Single.fromCallable(() -> {
                        String htmlToLoad = isTranslatedView
                                ? webViewViewModel.getHtmlById(currentId)
                                : webViewViewModel.getOriginalHtmlById(currentId);

                        if (htmlToLoad == null) {
                            htmlToLoad = metadata.getString("html");
                        }
                        return htmlToLoad;
                    })
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    htmlToLoad -> {
                                        if (htmlToLoad != null) {
                                            loadHtmlIntoWebView(htmlToLoad);
                                        }

                                        offlineButton.setVisible(false);
                                        reloadButton.setVisible(true);
                                        bookmarkButton.setVisible(true);
                                        browserButton.setVisible(true);
                                        highlightTextButton.setVisible(true);
                                        updateToggleTranslationVisibility();
                                    },
                                    throwable -> {
                                        Log.e(TAG, "Error in setupReadingWebView", throwable);
                                    }
                            )
            );
        }
    }

    @Override
    public void highlightText(String searchText) {
        if (!isReadingMode && sharedPreferencesRepository.getHighlightText()) {
            String text = searchText.trim();
            if (webViewViewModel.endsWithBreak(text)) {
                text = text.substring(0, text.length() - 1);
            }
            Log.d(TAG, "Highlighted text: " + text);
            String finalText = text.trim();
            ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> webView.findAllAsync(finalText));
        }
    }

    @Override
    public void finishedSetup() {
        ContextCompat.getMainExecutor(getApplicationContext()).execute(new Runnable() {
            @Override
            public void run() {
                if (!isReadingMode) {
                    loading.setVisibility(View.INVISIBLE);
                    functionButtons.setVisibility(View.VISIBLE);
                    functionButtons.setAlpha(1.0f);
                }
                reloadButton.setVisible(true);
                bookmarkButton.setVisible(true);
                highlightTextButton.setVisible(true);
                updateToggleTranslationVisibility();
                if (showOfflineButton) {
                    offlineButton.setVisible(true);
                }
            }
        });
    }

    private EntryInfo cachedEntryInfo = null;

    private String getLanguageForCurrentView(long entryId, boolean isTranslated, String defaultLang) {
        if (isTranslated) {
            String translationLang = sharedPreferencesRepository.getDefaultTranslationLanguage();
            Log.d(TAG, "getLanguageForCurrentView: Using TRANSLATED lang=" + translationLang);
            return translationLang;
        }

        if (cachedEntryInfo == null) {
            cachedEntryInfo = webViewViewModel.getEntryInfoById(entryId);
        }

        String feedLang = (cachedEntryInfo != null && cachedEntryInfo.getFeedLanguage() != null && !cachedEntryInfo.getFeedLanguage().trim().isEmpty())
                ? cachedEntryInfo.getFeedLanguage()
                : defaultLang;

        Log.d(TAG, "getLanguageForCurrentView: Using FEED lang=" + feedLang + " (isTranslated=" + isTranslated + ")");
        return feedLang;
    }

    @Override
    public void makeSnackbar(String message) {
        Snackbar.make(findViewById(R.id.webView_view), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void reload() {
        if (currentId <= 0) {
            Log.w(TAG, "reload() aborted: invalid currentId");
            return;
        }

        Log.d(TAG, "Reload triggered for entryId: " + currentId);

        webViewViewModel.resetEntry(currentId);
        webViewViewModel.clearLiveEntryCache(currentId);
        if (getIntent().getBooleanExtra("forceOriginal", false)) {
            sharedPreferencesRepository.setIsTranslatedView(currentId, false);
        }

        if (!isReadingMode) {
            mMediaBrowserHelper.getTransportControls().stop();
        }

        Intent intent = getIntent();
        intent.putExtra("entry_id", currentId);

        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    public void askForReload(long feedId) {
        ReloadDialog dialog = new ReloadDialog(this, feedId, R.string.reload_confirmation, R.string.reload_suggestion_message);
        dialog.show(getSupportFragmentManager(), ReloadDialog.TAG);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (autoTranslationObserver != null && checkAutoTranslated != null) {
            autoTranslationObserver.removeObserver(checkAutoTranslated);
        }

        if (retryHandler != null) {
            retryHandler.removeCallbacksAndMessages(null);
        }

        if (isReadingMode) {
            switchPlayModeButton.setVisible(false);
            functionButtonsReadingMode.setVisibility(View.INVISIBLE);
        } else {
            functionButtons.setVisibility(View.INVISIBLE);
            switchReadModeButton.setVisible(false);
        }
        reloadButton.setVisible(false);
        bookmarkButton.setVisible(false);
        translationButton.setVisible(false);
        highlightTextButton.setVisible(false);
        compositeDisposable.dispose();
        textUtil.onDestroy();

        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
            webView = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!isReadingMode) {
            mMediaBrowserHelper.onStart();

            MediaControllerCompat mediaController = mMediaBrowserHelper.getMediaController();
            if (mediaController != null) {
                MediaControllerCompat.setMediaController(this, mediaController);
            }
        }
    }

    @Override
    public void onStop() {
        if (isReadingMode) {
            ttsExtractor.setCallback((WebViewListener) null);
        } else {
            ttsPlayer.setWebViewCallback(null);
            mMediaBrowserHelper.onStop();
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        ttsPlayer.setWebViewConnected(false);
        ttsPlayer.setUiControlPlayback(false);

        if (webView != null) {
            webView.onPause();
            if (currentId != 0) {
                sharedPreferencesRepository.setScrollX(currentId, webView.getScrollX());
                sharedPreferencesRepository.setScrollY(currentId, webView.getScrollY());
                sharedPreferencesRepository.setIsTranslatedView(currentId, isTranslatedView);
            }
        }

        MediaControllerCompat mediaController = mMediaBrowserHelper.getMediaController();
        if (mediaController != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
            Log.d(TAG, "MediaController callback unregistered");
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        ttsPlayer.setWebViewConnected(true);

        updatePlayPauseButtonIcon(ttsPlayer.isSpeaking() && !ttsPlayer.isPausedManually());

        Log.d(TAG, "onResume: isSpeaking=" + ttsPlayer.isSpeaking() + ", isPausedManually=" + ttsPlayer.isPausedManually());

        if (!isReadingMode) {
            mMediaBrowserHelper.onStart();
            MediaControllerCompat mediaController = mMediaBrowserHelper.getMediaController();
            if (mediaController != null) {
                mediaController.registerCallback(mediaControllerCallback);
            }
        }

    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged: orientation changed, activity not recreated.");
    }

    private class WebClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d(TAG, "WebClient: onPageStarted - loadingWebView visible.");
            webViewViewModel.setLoadingState(true);
            currentLoadingUrl = url;
            retryHandler.removeCallbacksAndMessages(null);
            if (clearHistory) {
                clearHistory = false;
                webView.clearHistory();
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.e(TAG, "Page load error: " + description + " (code: " + errorCode + ")");
            handlePageLoadFailure(failingUrl, "Page load error: " + description);
        }

        @Override
        public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                    android.webkit.WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e(TAG, "Main frame error: " + error.getDescription());
                    handlePageLoadFailure(request.getUrl().toString(), error.getDescription().toString());
                }
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            Log.d(TAG, "WebClient: onPageCommitVisible - loadingWebView hidden.");
            webViewViewModel.setLoadingState(false);
            if (content != null) {
                if (currentId != ttsPlaylist.getPlayingId()) {
                    ttsPlaylist.updatePlayingId(currentId);
                    mMediaBrowserHelper.getTransportControls().sendCustomAction("autoPlay", null);
                }
                functionButtons.setVisibility(View.VISIBLE);
                functionButtons.setAlpha(1.0f);
                reloadButton.setVisible(true);
                bookmarkButton.setVisible(true);
                highlightTextButton.setVisible(true);
            } else {
                if (currentId != ttsPlaylist.getPlayingId()) {
                    ttsPlaylist.updatePlayingId(currentId);
                }
            }
        }
    }

    private class ReadingWebClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d(TAG, "ReadingWebClient: onPageStarted - loadingWebView visible.");
            webViewViewModel.setLoadingState(true);
            currentLoadingUrl = url;
            retryHandler.removeCallbacksAndMessages(null);
            ttsExtractor.setCallback(WebViewActivity.this);
            if (clearHistory) {
                clearHistory = false;
                webView.clearHistory();
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.e(TAG, "Page load error (Reading mode): " + description + " (code: " + errorCode + ")");
            handlePageLoadFailure(failingUrl, "Page load error: " + description);
        }

        @Override
        public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                    android.webkit.WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e(TAG, "Main frame error (Reading mode): " + error.getDescription());
                    handlePageLoadFailure(request.getUrl().toString(), error.getDescription().toString());
                }
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            loading.setVisibility(View.INVISIBLE);
            Log.d(TAG, "ReadingWebClient: onPageCommitVisible - loadingWebView hidden.");
            webViewViewModel.setLoadingState(false);
        }
    }

    private class MediaBrowserConnection extends MediaBrowserHelper {
        private MediaBrowserConnection(Context context) {
            super(context, TtsService.class);
        }

        @Override
        protected void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);

            Log.d(TAG, "MediaBrowser children loaded, parentId: " + parentId);
            
            final MediaControllerCompat mediaController = getMediaController();
            if (mediaController != null) {
                Log.d(TAG, "MediaController obtained, setting up TTS callbacks");
                ttsPlayer.setWebViewCallback(WebViewActivity.this);
                ttsPlayer.setWebViewConnected(true);
                mediaController.getTransportControls().prepare();
                Log.d(TAG, "Transport controls prepare() called");
            } else {
                Log.e(TAG, "MediaController is null in onChildrenLoaded!");
            }
        }
    }

    private class MediaBrowserListener extends MediaControllerCompat.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            isPlaying = state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING;
            updatePlayPauseButtonIcon(isPlaying);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (metadata == null) {
                return;
            }
            clearHistory = true;
            runOnUiThread(() -> {
                loading.setVisibility(View.VISIBLE);
                loading.setProgress(10);
            });
            functionButtons.setVisibility(View.VISIBLE);
            functionButtons.setAlpha(0.5f);
            reloadButton.setVisible(false);
            bookmarkButton.setVisible(false);
            highlightTextButton.setVisible(false);
            showOfflineButton = false;

            content = metadata.getString("content");
            bookmark = metadata.getString("bookmark");
            currentLink = metadata.getString("link");
            currentId = Long.parseLong(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID));
            updateToggleTranslationVisibility();
            feedId = metadata.getLong("feedId");

            if (bookmark == null || bookmark.equals("N")) {
                bookmarkButton.setIcon(R.drawable.ic_bookmark_outline);
            } else {
                bookmarkButton.setIcon(R.drawable.ic_bookmark_filled);
            }

            isTranslatedView = sharedPreferencesRepository.getIsTranslatedView(currentId);
            boolean isWebViewMode = sharedPreferencesRepository.getWebViewMode(currentId);

            if (isWebViewMode) {
                webView.loadUrl(currentLink);
                Log.d(TAG, "Restoring web view mode: " + currentLink);
                browserButton.setVisible(false);
                offlineButton.setVisible(true);
                showOfflineButton = false;
            } else {
                compositeDisposable.add(
                        Single.fromCallable(() -> {
                                    String htmlToLoad = isTranslatedView
                                            ? webViewViewModel.getHtmlById(currentId)
                                            : webViewViewModel.getOriginalHtmlById(currentId);

                                    if (htmlToLoad == null) {
                                        htmlToLoad = metadata.getString("html");
                                    }
                                    return htmlToLoad;
                                })
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        htmlToLoad -> {
                                            if (htmlToLoad != null) {
                                                loadHtmlIntoWebView(htmlToLoad);
                                                browserButton.setVisible(true);
                                                offlineButton.setVisible(false);
                                                showOfflineButton = false;
                                            } else {
                                                webView.loadUrl(currentLink);
                                                Log.d(TAG, "Fallback: loading live URL - " + currentLink);
                                                browserButton.setVisible(false);
                                                showOfflineButton = true;
                                            }

                                            if (ttsPlayer.isWebViewConnected()) {
                                                ttsPlayer.setUiControlPlayback(true);
                                            }
                                        },
                                        throwable -> {
                                            Log.e(TAG, "Error loading metadata", throwable);
                                            loading.setVisibility(View.GONE);
                                        }
                                )
                );
            }
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
        }
    }

    private void updatePlayPauseButtonIcon(boolean playing) {
        int iconRes = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        playPauseButton.setIcon(ContextCompat.getDrawable(this, iconRes));
    }

    /**
     * Check if JavaScript executed successfully by injecting a test script
     */
    private void checkJavaScriptExecution() {
        // Cancel any pending checks or reloads to prevent overlapping logic
        retryHandler.removeCallbacksAndMessages(null);

        retryHandler.postDelayed(() -> {
            webView.evaluateJavascript(
                "(function() { " +
                "   try { " +
                "       var ready = document.readyState === 'complete' || document.readyState === 'interactive';" +
                "       var hasContent = document.body !== null && document.body.innerText.trim().length > 0;" +
                "       return ready && hasContent; " +
                "   } catch(e) { " +
                "       return false; " +
                "   } " +
                "})();",
                result -> {
                    boolean isPageHealthy = "true".equals(result);
                    Log.d(TAG, "JavaScript execution check: " + (isPageHealthy ? "SUCCESS" : "FAILED") + ", result=" + result);

                    if (!isPageHealthy) {
                        handlePageLoadFailure(currentLoadingUrl, "JavaScript execution failed or incomplete page load");
                    } else {
                        // Page loaded successfully, reset retry count
                        pageLoadRetryCount = 0;
                    }
                }
            );
        }, 1000); // Wait 1 second after page finish to check
    }

    /**
     * Handle page load failures with automatic retry or user prompt
     */
    private void handlePageLoadFailure(String failedUrl, String errorMessage) {
        if (failedUrl == null || !failedUrl.equals(currentLoadingUrl)) {
            return; // Ignore errors from sub-resources
        }

        runOnUiThread(() -> {
            loading.setVisibility(View.GONE);

            if (pageLoadRetryCount < MAX_RETRY_ATTEMPTS) {
                pageLoadRetryCount++;
                Log.w(TAG, "Auto-retry attempt " + pageLoadRetryCount + "/" + MAX_RETRY_ATTEMPTS + " for URL: " + failedUrl);

                makeSnackbar("Page failed to load. Retrying (" + pageLoadRetryCount + "/" + MAX_RETRY_ATTEMPTS + ")...");

                // Retry after a delay
                retryHandler.postDelayed(() -> {
                    webView.reload();
                }, 2000);
            } else {
                // Max retries reached, prompt user
                Log.e(TAG, "Max retry attempts reached for URL: " + failedUrl);
                showRetryDialog(failedUrl, errorMessage);
            }
        });
    }

    /**
     * Show dialog to prompt user to retry or cancel
     */
    private void showRetryDialog(String url, String errorMessage) {
        new AlertDialog.Builder(this)
            .setTitle("Page Load Failed")
            .setMessage("The page failed to load correctly after " + MAX_RETRY_ATTEMPTS + " attempts.\n\n" +
                       "Error: " + errorMessage + "\n\n" +
                       "Would you like to retry loading the page?")
            .setPositiveButton("Retry", (dialog, which) -> {
                pageLoadRetryCount = 0;
                webView.reload();
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                pageLoadRetryCount = 3;
                makeSnackbar("Page load cancelled");
                dialog.dismiss();
            })
            .setNeutralButton("Reload with Delay", (dialog, which) -> {
                pageLoadRetryCount = 0;
                ReloadDialog reloadDialog = new ReloadDialog(this, feedId,
                    R.string.reload_confirmation, R.string.reload_suggestion_message);
                reloadDialog.show(getSupportFragmentManager(), ReloadDialog.TAG);
                dialog.dismiss();
            })
            .setCancelable(false)
            .show();
    }
}

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private volatile boolean isDestroyed = false;
    private boolean isWebViewContentLoaded = false;
    // Share
    private ActivityWebviewBinding binding;
    private WebViewViewModel webViewViewModel;
    private WebView webView;
    private LinearProgressIndicator loading;
    private MenuItem browserButton;
    private MenuItem offlineButton;
    private MenuItem reloadButton;
    private MenuItem bookmarkButton;
    private MenuItem highlightTextButton;
    private MenuItem backgroundMusicButton;
    private String currentLink;
    private long currentId;
    private long feedId;
    private String feedLanguage;
    private String html;
    private String content;
    private String bookmark;
    private boolean isPlaying;
    private boolean isReadingMode;
    private boolean showOfflineButton;
    private boolean clearHistory;
    private boolean isTranslatedView = true;
    private MaterialToolbar toolbar;
    private FloatingActionButton translateFab;
    private FloatingActionButton summaryFab;
    private FloatingActionButton translateSummaryFab;
    private LinearLayout fabContainer;

    private String originalHtmlForSummary;
    private String summaryHtml;
    private String translatedSummaryHtml;
    private boolean isSummaryView = false;
    private boolean hasGeneratedSummary = false;
    private boolean hasTranslatedSummary = false;
    private boolean isWaitingForArticleContent = false;
    private String currentHighlightText = null;

    private enum ActiveFab { NONE, TRANSLATE, SUMMARY, TRANSLATE_SUMMARY }
    private ActiveFab activeFab = ActiveFab.NONE;

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
        builder.setTitle(getString(R.string.default_translation_language));

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

        isTranslatedView = true;
        sharedPreferencesRepository.setIsTranslatedView(currentId, true);

        final String[] translatedContentHolder = new String[1];

        compositeDisposable.add(
                Single.fromCallable(() -> {
                    if (webViewViewModel.getOriginalHtmlById(currentId) == null && originalHtml != null) {
                        webViewViewModel.updateOriginalHtml(originalHtml, currentId);
                        entryRepository.updateOriginalHtml(originalHtml, currentId);
                        Log.d(TAG, "Original HTML backed up from method parameter.");
                    }

                    if (translatedHtml == null || translatedHtml.trim().isEmpty()) {
                        throw new Exception("Translated HTML is null or empty");
                    }

                    Document doc = Jsoup.parse(translatedHtml);
                    if (doc.head() != null) {
                        doc.head().append(webViewViewModel.getStyle());
                    }

                    String finalHtml = doc.html();
                    if (finalHtml == null) {
                        throw new Exception("Failed to generate final HTML from parsed document");
                    }

                    webViewViewModel.updateHtml(finalHtml, currentId);
                    entryRepository.updateHtml(finalHtml, currentId);

                    String translatedContent = textUtil.extractHtmlContent(finalHtml, "--####--");
                    translatedContentHolder[0] = translatedContent;
                    webViewViewModel.updateTranslated(translatedContent, currentId);
                    webViewViewModel.updateEntryTranslatedField(currentId, translatedContent);
                    entryRepository.updateTranslatedText(translatedContent, currentId);

                    return finalHtml;
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> {
                            loading.setVisibility(View.GONE);
                        })
                        .subscribe(
                                finalHtml -> {
                                    Log.d(TAG, "Translation completed");
                                    if (activeFab != ActiveFab.TRANSLATE_SUMMARY) {
                                        setActiveFab(ActiveFab.TRANSLATE);
                                    }
                                    loadHtmlIntoWebView(finalHtml);

                                    String lang = getLanguageForCurrentView(currentId, true, "en");
                                    String content = translatedContentHolder[0];
                                    if (content != null && !content.isEmpty()) {
                                        ttsExtractor.setCurrentLanguage(lang, true);
                                        ttsPlayer.extract(currentId, feedId, content, lang);
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

    private void showSetupRequiredDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                    Intent intent = new Intent(this, my.mmu.Kaixuanrssnewsreader.ui.main.MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra("navigate_to_settings", true);
                    startActivity(intent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void translate() {
        String apiKey = sharedPreferencesRepository.getApiKey();
        if (!sharedPreferencesRepository.getApiProvider().isLocal() && (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here"))) {
            showSetupRequiredDialog(getString(R.string.setup_required_title), getString(R.string.setup_required_api_key_message));
            return;
        }

        if (sharedPreferencesRepository.getApiProvider().isLocal() && !textUtil.isLocalModelReady()) {
            showSetupRequiredDialog("Model Not Downloaded", "Please download the local model from Settings before using this feature.");
            return;
        }

        if (!sharedPreferencesRepository.hasModel()) {
            sharedPreferencesRepository.setModel(sharedPreferencesRepository.getApiProvider().getDefaultModel());
        }

        String originalHtml = webViewViewModel.getOriginalHtmlById(currentId);
        String contentToTranslate = (originalHtml != null) ? originalHtml : webViewViewModel.getHtmlById(currentId);

        if (contentToTranslate == null) {
            makeSnackbar("No content to translate.");
            return;
        }

        makeSnackbar("Translation in progress");
        loading.setVisibility(View.VISIBLE);
        loading.setProgress(0);

        EntryInfo entryInfo = webViewViewModel.getEntryInfoById(currentId);
        String title = (entryInfo != null) ? entryInfo.getEntryTitle() : "";
        if (entryInfo != null && entryInfo.getFeedLanguage() != null) {
            feedLanguage = entryInfo.getFeedLanguage();
        }

        if (targetLanguage == null || targetLanguage.isEmpty()) {
            targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();
        }
        if (targetLanguage == null || targetLanguage.isEmpty()) {
            targetLanguage = java.util.Locale.getDefault().getLanguage();
        }

        compositeDisposable.add(
            Single.fromCallable(() -> {
                String content = textUtil.extractHtmlContent(contentToTranslate, " ");
                if (content == null || content.trim().isEmpty()) {
                    throw new Exception("No content to translate");
                }
                return content;
            })
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
                        performTranslation(sourceLanguage, targetLanguage, contentToTranslate, title);
                    },
                    error -> {
                        Log.e(TAG, "Language identification failed", error);
                        String sourceLanguage = feedLanguage != null ? feedLanguage : "en";
                        performTranslation(sourceLanguage, targetLanguage, contentToTranslate, title);
                    }
                )
        );
    }

    private String getSystemLanguage() {
        return java.util.Locale.getDefault().getLanguage();
    }

    private void performAutoSummary() {
        if (hasGeneratedSummary) {
            if (isSummaryView) {
                switchToArticleView();
            } else {
                switchToSummaryView();
            }
            return;
        }

        String apiKey = sharedPreferencesRepository.getApiKey();
        if (!sharedPreferencesRepository.getApiProvider().isLocal() && (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here"))) {
            showSetupRequiredDialog(getString(R.string.setup_required_title), getString(R.string.setup_required_api_key_message));
            return;
        }

        if (sharedPreferencesRepository.getApiProvider().isLocal() && !textUtil.isLocalModelReady()) {
            showSetupRequiredDialog("Model Not Downloaded", "Please download the local model from Settings before using this feature.");
            return;
        }

        String contentToSummarize = webViewViewModel.getContentById(currentId);

        if (contentToSummarize == null || contentToSummarize.trim().isEmpty()) {
            makeSnackbar("No content to summarize.");
            return;
        }

        if (originalHtmlForSummary == null) {
            String currentHtml = webViewViewModel.getHtmlById(currentId);
            originalHtmlForSummary = currentHtml;
        }

        String langToSummarize = feedLanguage != null ? feedLanguage : "en";

        makeSnackbar("Generating summary...");
        loading.setVisibility(View.VISIBLE);
        loading.setProgress(0);

        int summaryLength = sharedPreferencesRepository.getSummaryLength();

        compositeDisposable.add(
            textUtil.summarizeText(contentToSummarize, summaryLength)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> {
                    loading.setVisibility(View.GONE);
                })
                .subscribe(
                    summary -> {
                        loading.setVisibility(View.GONE);
                        summaryHtml = formatSummaryAsHtml(summary);
                        hasGeneratedSummary = true;
                        sharedPreferencesRepository.setSummary(currentId, summary, langToSummarize);
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
        String savedTranslatedSummary = sharedPreferencesRepository.getTranslatedSummary(currentId);

        if (savedSummary != null && !savedSummary.isEmpty()) {
            Log.d(TAG, "loadSavedSummaryOrGenerate: Found saved summary, restoring view");
            if (originalHtmlForSummary == null) {
                String currentHtml = webViewViewModel.getHtmlById(currentId);
                originalHtmlForSummary = currentHtml;
            }
            summaryHtml = formatSummaryAsHtml(savedSummary);
            hasGeneratedSummary = true;

            if (savedTranslatedSummary != null && !savedTranslatedSummary.isEmpty()) {
                translatedSummaryHtml = formatSummaryAsHtml(savedTranslatedSummary);
                hasTranslatedSummary = true;
            }

            String savedActiveFab = sharedPreferencesRepository.getActiveFab(currentId);
            if ("SUMMARY".equals(savedActiveFab)) {
                isSummaryView = sharedPreferencesRepository.getIsSummaryView(currentId);
                if (isSummaryView) {
                    switchToSummaryView();
                }
            } else if ("TRANSLATE_SUMMARY".equals(savedActiveFab)) {
                if (hasTranslatedSummary) {
                    isSummaryView = true;
                    switchToTranslatedSummaryView();
                }
            }
        } else if (sharedPreferencesRepository.getAutoTranslateSummary()) {
            Log.d(TAG, "loadSavedSummaryOrGenerate: Auto translate+summary enabled, triggering");
            performTranslateAndSummary();
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
            setActiveFab(ActiveFab.SUMMARY);
            makeSnackbar("Showing summary view");

            entryRepository.updateSentCount(0, currentId);

            String summaryText = sharedPreferencesRepository.getSummary(currentId);
            if (summaryText != null && !summaryText.isEmpty()) {
                EntryInfo entryInfo = webViewViewModel.getEntryInfoById(currentId);
                String entryTitle = entryInfo != null ? entryInfo.getEntryTitle() : null;
                String contentWithTitle = (entryTitle != null ? entryTitle + ". " : "") + summaryText;
                String lang = feedLanguage != null ? feedLanguage : "en";
                Log.d(TAG, "switchToSummaryView: Re-extracting TTS with summary content, language: " + lang);
                ttsPlayer.extract(currentId, feedId, contentWithTitle, lang);
            }
        }
    }

    private void switchToTranslatedSummaryView() {
        if (translatedSummaryHtml != null) {
            webView.loadDataWithBaseURL("file///android_res/", translatedSummaryHtml, "text/html", "UTF-8", null);
            isSummaryView = true;
            sharedPreferencesRepository.setIsSummaryView(currentId, true);
            setActiveFab(ActiveFab.TRANSLATE_SUMMARY);
            makeSnackbar("Showing translated summary");

            entryRepository.updateSentCount(0, currentId);

            String translatedSummaryText = sharedPreferencesRepository.getTranslatedSummary(currentId);
            if (translatedSummaryText != null && !translatedSummaryText.isEmpty()) {
                EntryInfo entryInfo = webViewViewModel.getEntryInfoById(currentId);
                String entryTitle = entryInfo != null ? entryInfo.getEntryTitle() : null;
                String contentWithTitle = (entryTitle != null ? entryTitle + ". " : "") + translatedSummaryText;
                String lang = sharedPreferencesRepository.getDefaultTranslationLanguage();
                Log.d(TAG, "switchToTranslatedSummaryView: Re-extracting TTS, language: " + lang);
                ttsPlayer.extract(currentId, feedId, contentWithTitle, lang);
            }
        }
    }

    private void switchToArticleView() {
        isSummaryView = false;
        sharedPreferencesRepository.setIsSummaryView(currentId, false);
        setActiveFab(ActiveFab.NONE);
        makeSnackbar("Showing article view");

        entryRepository.updateSentCount(0, currentId);

        String htmlToLoad = isTranslatedView
                ? webViewViewModel.getHtmlById(currentId)
                : webViewViewModel.getOriginalHtmlById(currentId);
        if (htmlToLoad == null) {
            htmlToLoad = originalHtmlForSummary;
        }
        if (htmlToLoad != null) {
            loadHtmlIntoWebView(htmlToLoad);
        }

        Entry entry = webViewViewModel.getEntryById(currentId);
        if (entry != null) {
            String contentToRead = isTranslatedView ? entry.getTranslated() : entry.getContent();
            if (contentToRead != null && !contentToRead.isEmpty()) {
                String lang = getLanguageForCurrentView(currentId, isTranslatedView, "en");
                Log.d(TAG, "switchToArticleView: Re-extracting TTS");
                ttsPlayer.extract(currentId, feedId, contentToRead, lang);
            }
        }
    }

    private void setActiveFab(ActiveFab fab) {
        activeFab = fab;
        sharedPreferencesRepository.setActiveFab(currentId, fab.name());
        updateFabAppearance();
    }

    private void updateFabAppearance() {
        if (translateFab == null || summaryFab == null || translateSummaryFab == null) return;
        float inactiveAlpha = 0.6f;
        float activeAlpha = 1.0f;

        translateFab.setAlpha(activeFab == ActiveFab.TRANSLATE ? activeAlpha : inactiveAlpha);
        summaryFab.setAlpha(activeFab == ActiveFab.SUMMARY ? activeAlpha : inactiveAlpha);
        translateSummaryFab.setAlpha(activeFab == ActiveFab.TRANSLATE_SUMMARY ? activeAlpha : inactiveAlpha);
    }

    private void onTranslateFabClicked() {
        if (activeFab == ActiveFab.TRANSLATE) {
            isTranslatedView = false;
            sharedPreferencesRepository.setIsTranslatedView(currentId, false);
            if (isSummaryView) {
                switchToArticleView();
            } else {
                setActiveFab(ActiveFab.NONE);
                String htmlToLoad = webViewViewModel.getOriginalHtmlById(currentId);
                if (htmlToLoad != null) {
                    loadHtmlIntoWebView(htmlToLoad);
                }
                Entry entry = webViewViewModel.getEntryById(currentId);
                if (entry != null) {
                    String contentToRead = entry.getContent();
                    if (contentToRead != null && !contentToRead.isEmpty()) {
                        String lang = getLanguageForCurrentView(currentId, false, "en");
                        ttsPlayer.extract(currentId, feedId, contentToRead, lang);
                    }
                }
            }
            return;
        }
        if (isSummaryView) {
            isSummaryView = false;
            sharedPreferencesRepository.setIsSummaryView(currentId, false);
        }

        Entry entry = webViewViewModel.getEntryById(currentId);
        if (entry != null) {
            boolean hasTranslation = entry.getOriginalHtml() != null
                    && entry.getHtml() != null
                    && !entry.getOriginalHtml().equals(entry.getHtml());
            if (hasTranslation) {
                isTranslatedView = true;
                sharedPreferencesRepository.setIsTranslatedView(currentId, true);
                setActiveFab(ActiveFab.TRANSLATE);
                String htmlToLoad = entry.getHtml();
                if (htmlToLoad != null) {
                    loadHtmlIntoWebView(htmlToLoad);
                }
                String contentToRead = entry.getTranslated();
                if (contentToRead != null && !contentToRead.isEmpty()) {
                    String lang = getLanguageForCurrentView(currentId, true, "en");
                    ttsExtractor.setCurrentLanguage(lang, true);
                    ttsPlayer.extract(currentId, feedId, contentToRead, lang);
                }
                return;
            }
        }

        setActiveFab(ActiveFab.TRANSLATE);
        translate();
    }

    private void onSummaryFabClicked() {
        if (activeFab == ActiveFab.SUMMARY) {
            switchToArticleView();
            return;
        }
        if (activeFab == ActiveFab.TRANSLATE_SUMMARY) {
            if (hasGeneratedSummary) {
                switchToSummaryView();
            } else {
                performAutoSummary();
            }
            return;
        }
        if (activeFab == ActiveFab.TRANSLATE) {
            isSummaryView = false;
            sharedPreferencesRepository.setIsSummaryView(currentId, false);
        }
        performAutoSummary();
    }

    private void onTranslateSummaryFabClicked() {
        if (activeFab == ActiveFab.TRANSLATE_SUMMARY) {
            switchToArticleView();
            return;
        }
        if (activeFab == ActiveFab.SUMMARY) {
            if (hasTranslatedSummary) {
                switchToTranslatedSummaryView();
            } else {
                performTranslateAndSummary();
            }
            return;
        }
        performTranslateAndSummary();
    }

    private void performTranslateAndSummary() {
        if (hasTranslatedSummary) {
            if (activeFab == ActiveFab.TRANSLATE_SUMMARY && isSummaryView) {
                switchToArticleView();
            } else {
                switchToTranslatedSummaryView();
            }
            return;
        }

        String apiKey = sharedPreferencesRepository.getApiKey();
        if (!sharedPreferencesRepository.getApiProvider().isLocal() && (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here"))) {
            showSetupRequiredDialog(getString(R.string.setup_required_title), getString(R.string.setup_required_api_key_message));
            return;
        }

        if (sharedPreferencesRepository.getApiProvider().isLocal() && !textUtil.isLocalModelReady()) {
            showSetupRequiredDialog("Model Not Downloaded", "Please download the local model from Settings before using this feature.");
            return;
        }

        String contentToSummarize = webViewViewModel.getContentById(currentId);
        if (contentToSummarize == null || contentToSummarize.trim().isEmpty()) {
            makeSnackbar("No content to summarize.");
            return;
        }

        if (originalHtmlForSummary == null) {
            String currentHtml = webViewViewModel.getHtmlById(currentId);
            originalHtmlForSummary = currentHtml;
        }

        if (targetLanguage == null || targetLanguage.isEmpty()) {
            targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();
        }
        if (targetLanguage == null || targetLanguage.isEmpty()) {
            targetLanguage = java.util.Locale.getDefault().getLanguage();
        }

        makeSnackbar("Summarizing and translating...");
        loading.setVisibility(View.VISIBLE);
        loading.setProgress(0);

        int summaryLength = sharedPreferencesRepository.getSummaryLength();
        String sourceLang = feedLanguage != null ? feedLanguage : "en";

        compositeDisposable.add(
            textUtil.summarizeAndTranslateText(contentToSummarize, summaryLength, sourceLang, targetLanguage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> loading.setVisibility(View.GONE))
                .subscribe(
                    result -> {
                        loading.setVisibility(View.GONE);
                        String lang = sharedPreferencesRepository.getDefaultTranslationLanguage();
                        sharedPreferencesRepository.setTranslatedSummary(currentId, result, lang);
                        translatedSummaryHtml = formatSummaryAsHtml(result);
                        hasTranslatedSummary = true;
                        switchToTranslatedSummaryView();
                    },
                    error -> {
                        Log.e(TAG, "Translate+Summary failed", error);
                        loading.setVisibility(View.GONE);
                        String errorMsg = error != null && error.getMessage() != null
                                ? error.getMessage() : "Translate+Summary failed.";
                        makeSnackbar(errorMsg);
                    }
                )
        );
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
                .doFinally(() -> {
                    loading.setVisibility(View.GONE);
                })
                .subscribe(
                        translatedHtml -> {
                            Log.d(TAG, "Translation completed");
                            doWhenTranslationFinish(webViewViewModel.getLastVisitedEntry(), originalHtml, translatedHtml);
                        },
                        throwable -> {
                            Log.e(TAG, "Translation failed", throwable);
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
        Log.d(TAG, "API key configured: " + sharedPreferencesRepository.hasApiKey());

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
        highlightTextButton = toolbar.getMenu().findItem(R.id.highlightText);
        backgroundMusicButton = toolbar.getMenu().findItem(R.id.toggleBackgroundMusic);
        switchReadModeButton = toolbar.getMenu().findItem(R.id.switchReadMode);
        switchPlayModeButton = toolbar.getMenu().findItem(R.id.switchPlayMode);

        highlightTextButton.setTitle(sharedPreferencesRepository.getHighlightText()
                ? R.string.highlight_text_turn_off : R.string.highlight_text_turn_on);
        backgroundMusicButton.setTitle(sharedPreferencesRepository.getBackgroundMusic()
                ? R.string.background_music_turn_off : R.string.background_music_turn_on);

        fabContainer = binding.fabContainer;
        translateFab = binding.translateFab;
        summaryFab = binding.summaryFab;
        translateSummaryFab = binding.translateSummaryFab;

        translateFab.setOnClickListener(v -> onTranslateFabClicked());
        summaryFab.setOnClickListener(v -> onSummaryFabClicked());
        translateSummaryFab.setOnClickListener(v -> onTranslateSummaryFabClicked());
    }

    private void loadHtmlIntoWebView(String html) {
        if (html == null || html.trim().isEmpty()) {
            return;
        }

        final long targetEntryId = currentId;
        compositeDisposable.add(
                Single.fromCallable(() -> {
                    EntryInfo entryInfo = webViewViewModel.getEntryInfoById(targetEntryId);
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
                                    if (webView == null) {
                                        Log.e(TAG, "WebView is null, cannot load HTML");
                                        return;
                                    }
                                    if (targetEntryId != currentId) {
                                        Log.d(TAG, "Skipping HTML load for stale article. targetEntryId=" + targetEntryId + ", currentId=" + currentId);
                                        return;
                                    }
                                    String savedSummary = sharedPreferencesRepository.getSummary(targetEntryId);
                                    boolean shouldKeepSummaryView = sharedPreferencesRepository.getIsSummaryView(targetEntryId)
                                            && savedSummary != null
                                            && !savedSummary.isEmpty();
                                    if (shouldKeepSummaryView) {
                                        Log.d(TAG, "Skipping normal HTML load because summary view is active for entryId=" + targetEntryId);
                                        return;
                                    }
                                    webView.loadDataWithBaseURL("file///android_res/", processedHtml, "text/html", "UTF-8", null);

                                    webView.postDelayed(() -> {
                                        if (webView != null) {
                                            int scrollX = sharedPreferencesRepository.getScrollX(targetEntryId);
                                            int scrollY = sharedPreferencesRepository.getScrollY(targetEntryId);
                                            webView.scrollTo(scrollX, scrollY);
                                        }
                                    }, 300);

                                    webView.postDelayed(() -> {
                                        if (webView != null && currentHighlightText != null && ttsPlayer != null && ttsPlayer.isPlaying()) {
                                            webView.findAllAsync(currentHighlightText);
                                        }
                                    }, 500);

                                    syncLoadingWithTts();
                                },
                                throwable -> {
                                    Log.e(TAG, "Error loading HTML into WebView", throwable);
                                }
                        )
        );
    }

    private void updateToggleTranslationVisibility() {
        Log.d(TAG, "updateToggleTranslationVisibility: isTranslatedView=" + isTranslatedView);
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
                translatedSummaryHtml = null;
                hasGeneratedSummary = false;
                hasTranslatedSummary = false;
                activeFab = ActiveFab.NONE;
                currentHighlightText = null;
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
                        if (info == null) {
                            throw new Exception("No article found");
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
                    feedLanguage = (entryInfo != null && entryInfo.getFeedLanguage() != null) ? entryInfo.getFeedLanguage() : "en";
                    targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();
                    makeSnackbar("Please wait, loading article...");

                    boolean hasTranslation = entry.getOriginalHtml() != null && entry.getHtml() != null && !entry.getOriginalHtml().equals(entry.getHtml());

                    if (hasTranslation) {
                        webViewViewModel.updateOriginalHtml(entry.getOriginalHtml(), entry.getId());
                        if (entry.getHtml() != null) {
                            webViewViewModel.updateHtml(entry.getHtml(), entry.getId());
                        }
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

                    String html = isTranslatedView ? entry.getHtml() : entry.getOriginalHtml();
                    content = entry.getContent();
                    boolean isHtmlAvailable = html != null && !html.trim().isEmpty();

                    if (!isHtmlAvailable && content != null && !content.trim().isEmpty()) {
                        isHtmlAvailable = true;
                        html = content;
                    }

                    if (!isHtmlAvailable) {
                        isWaitingForArticleContent = true;
                        makeSnackbar("Please wait, loading article...");
                        Log.d(TAG, "loadEntryContent: Article content not available yet, triggering re-extraction");
                        entryRepository.updatePriorityUnconditional(1, currentId);
                        ttsExtractor.extractAllEntries();
                    } else {
                        isWaitingForArticleContent = false;

                        if (!hasTranslation) {
                            if (sharedPreferencesRepository.getAutoTranslateSummary()) {
                                Log.d(TAG, "loadEntryContent: Auto translate+summary enabled");
                                setActiveFab(ActiveFab.TRANSLATE_SUMMARY);
                                performTranslateAndSummary();
                            } else if (sharedPreferencesRepository.getAutoTranslate()) {
                                Log.d(TAG, "loadEntryContent: Auto-translate enabled, triggering translation");
                                setActiveFab(ActiveFab.TRANSLATE);
                                translate();
                            }
                        } else if (hasTranslation && sharedPreferencesRepository.getAutoTranslate()) {
                            setActiveFab(ActiveFab.TRANSLATE);
                        }

                        loadSavedSummaryOrGenerate();
                    }

        Log.d("LoadEntry", "htmlToLoad (translated) = " + (html != null ? html.length() : "null"));

        String savedActiveFab = sharedPreferencesRepository.getActiveFab(currentId);
        String savedSummary = sharedPreferencesRepository.getSummary(currentId);
        String savedTranslatedSummary = sharedPreferencesRepository.getTranslatedSummary(currentId);
        boolean hasSummary = savedSummary != null && !savedSummary.isEmpty();
        boolean hasTransSummary = savedTranslatedSummary != null && !savedTranslatedSummary.isEmpty();

        if (hasSummary && summaryHtml == null) {
            summaryHtml = formatSummaryAsHtml(savedSummary);
            hasGeneratedSummary = true;
        }
        if (hasTransSummary && translatedSummaryHtml == null) {
            translatedSummaryHtml = formatSummaryAsHtml(savedTranslatedSummary);
            hasTranslatedSummary = true;
        }

        isSummaryView = sharedPreferencesRepository.getIsSummaryView(currentId);

        String contentToRead;
        if (isSummaryView && "TRANSLATE_SUMMARY".equals(savedActiveFab) && hasTransSummary) {
            String entryTitle = entry.getTitle();
            contentToRead = (entryTitle != null ? entryTitle + ". " : "") + savedTranslatedSummary;
            Log.d(TAG, "Using translated summary for TTS");
        } else if (isSummaryView && hasSummary) {
            String entryTitle = entry.getTitle();
            contentToRead = (entryTitle != null ? entryTitle + ". " : "") + savedSummary;
            Log.d(TAG, "Using summary for TTS with title: " + entryTitle);
        } else {
            contentToRead = isTranslatedView
                    ? entry.getTranslated()
                    : entry.getContent();
        }

        String lang;
        if (isSummaryView && "TRANSLATE_SUMMARY".equals(savedActiveFab)) {
            lang = sharedPreferencesRepository.getTranslatedSummaryLanguage(currentId);
            if (lang == null) lang = sharedPreferencesRepository.getDefaultTranslationLanguage();
        } else if (isSummaryView) {
            lang = feedLanguage != null ? feedLanguage : "en";
        } else {
            lang = getLanguageForCurrentView(currentId, isTranslatedView, "en");
        }

        if (!"NONE".equals(savedActiveFab)) {
            activeFab = ActiveFab.valueOf(savedActiveFab);
            updateFabAppearance();
        }

        String contentType = isSummaryView ? "Summary" : (isTranslatedView ? "Translated" : "Original");
        Log.d(TAG, "loadEntryContent - About to speak " + contentType);
        Log.d(TAG, "Language to use: " + lang);
        Log.d(TAG, "Text to read length: " + (contentToRead != null ? contentToRead.length() : 0));

        Log.d(TAG, "Calling setCurrentLanguage with: " + lang + ", lock=true");
        ttsExtractor.setCurrentLanguage(lang, true);

        boolean canExtractTts = (!isSummaryView && html != null && !html.trim().isEmpty())
                || (isSummaryView && contentToRead != null && !contentToRead.trim().isEmpty());

        if (isSummaryView && summaryHtml == null && savedSummary != null) {
            Log.d(TAG, "Restoring summaryHtml from savedSummary");
            summaryHtml = formatSummaryAsHtml(savedSummary);
        }

        final String finalLang = lang;
        final String finalContentToRead = contentToRead;

        if (canExtractTts) {
            if (isSummaryView && "TRANSLATE_SUMMARY".equals(savedActiveFab) && translatedSummaryHtml != null) {
                Log.d(TAG, "Loading translated summary view into WebView");
                webView.loadDataWithBaseURL("file///android_res/", translatedSummaryHtml, "text/html", "UTF-8", null);
            } else if (isSummaryView && summaryHtml != null) {
                Log.d(TAG, "Loading summary view into WebView");
                webView.loadDataWithBaseURL("file///android_res/", summaryHtml, "text/html", "UTF-8", null);
            } else {
                Log.d(TAG, "Loading normal view into WebView, isSummaryView=" + isSummaryView + ", summaryHtml=" + (summaryHtml != null ? "not null" : "null"));
                loadHtmlIntoWebView(html);
            }

            if (!ttsPlayer.isSameArticleState(entry.getId(), finalLang)) {
                compositeDisposable.add(
                        Completable.fromAction(() -> {
                            ttsPlayer.extract(entry.getId(), entry.getFeedId(), finalContentToRead, finalLang);
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
                        makeSnackbar("This article is missing.");
                        return;
                    }

                    try {
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

                        if (isWaitingForArticleContent) {
                            String html = isTranslatedView ? dbHtml : dbOriginal;
                            if (html != null && !html.trim().isEmpty()) {
                                isWaitingForArticleContent = false;
                                makeSnackbar("Article loaded successfully");

                                boolean hasTranslation = entry.getOriginalHtml() != null && entry.getHtml() != null && !entry.getOriginalHtml().equals(entry.getHtml());

                                if (!hasTranslation) {
                                    if (sharedPreferencesRepository.getAutoTranslateSummary()) {
                                        Log.d(TAG, "observeLiveEntry: Auto translate+summary enabled");
                                        setActiveFab(ActiveFab.TRANSLATE_SUMMARY);
                                        performTranslateAndSummary();
                                    } else if (sharedPreferencesRepository.getAutoTranslate()) {
                                        Log.d(TAG, "observeLiveEntry: Article content now available, triggering auto-translate");
                                        setActiveFab(ActiveFab.TRANSLATE);
                                        translate();
                                    }
                                }

                                loadSavedSummaryOrGenerate();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error accessing entry data, entry may have been deleted", e);
                    }
                });

                webViewViewModel.getOriginalHtmlLiveData().observe(this, originalHtml -> {
                    try {
                        updateToggleStateAndWebView(originalHtml, webViewViewModel.getTranslatedHtmlLiveData().getValue());
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating toggle state", e);
                    }
                });
                webViewViewModel.getTranslatedHtmlLiveData().observe(this, translatedHtml -> {
                    try {
                        updateToggleStateAndWebView(webViewViewModel.getOriginalHtmlLiveData().getValue(), translatedHtml);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating toggle state", e);
                    }
                });

    

            }

    private void updateToggleStateAndWebView(String originalHtml, String translatedHtml) {
        if (isSummaryView) {
            Log.d(TAG, "updateToggleStateAndWebView: Skipping WebView update in summary view");
            return;
        }

        boolean hasOriginal = originalHtml != null && !originalHtml.trim().isEmpty();
        boolean hasTranslated = translatedHtml != null && !translatedHtml.trim().isEmpty();

        String htmlToLoad = isTranslatedView ? translatedHtml : originalHtml;

        Log.d(TAG, "LiveEntry - Current Mode: " + (isTranslatedView ? "Translated" : "Original"));

        if (htmlToLoad != null && !htmlToLoad.trim().isEmpty()) {
            loadHtmlToWebView(htmlToLoad);
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
                            Completable.fromAction(() -> {
                                String originalHtmlFromDb = null;
                                String translatedHtmlFromDb = null;

                                try {
                                    originalHtmlFromDb = entryRepository.getOriginalHtmlById(currentId);
                                    translatedHtmlFromDb = entry.getHtml();
                                } catch (Exception e) {
                                    Log.e(TAG, "Entry may have been deleted", e);
                                }

                                if (originalHtmlFromDb != null) {
                                    webViewViewModel.updateOriginalHtml(originalHtmlFromDb, currentId);
                                    Log.d(TAG, "Original HTML restored from DB.");
                                }

                                if (translatedHtmlFromDb != null) {
                                    webViewViewModel.updateHtml(translatedHtmlFromDb, currentId);
                                    Log.d(TAG, "Translated HTML synced from auto translation.");
                                }

                                Log.d(TAG, "AutoTranslation - Final Original:\n" + webViewViewModel.getOriginalHtmlById(currentId));
                                Log.d(TAG, "AutoTranslation - Final Translated:\n" + webViewViewModel.getHtmlById(currentId));

                                webViewViewModel.triggerEntryRefresh(currentId);
                            })
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            () -> {
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

        final long targetEntryId = currentId;
        compositeDisposable.add(
                Single.fromCallable(() -> {
                    EntryInfo entryInfo = webViewViewModel.getEntryInfoById(targetEntryId);
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
                                    if (targetEntryId != currentId) {
                                        Log.d(TAG, "Skipping HTML load for stale article. targetEntryId=" + targetEntryId + ", currentId=" + currentId);
                                        return;
                                    }
                                    String savedSummary = sharedPreferencesRepository.getSummary(targetEntryId);
                                    boolean shouldKeepSummaryView = sharedPreferencesRepository.getIsSummaryView(targetEntryId)
                                            && savedSummary != null
                                            && !savedSummary.isEmpty();
                                    if (shouldKeepSummaryView) {
                                        Log.d(TAG, "Skipping normal HTML load because summary view is active for entryId=" + targetEntryId);
                                        return;
                                    }
                                    webView.loadDataWithBaseURL("file///android_res/", processedHtml, "text/html", "UTF-8", null);

                                    webView.postDelayed(() -> {
                                        if (webView != null && currentHighlightText != null && ttsPlayer != null && ttsPlayer.isPlaying()) {
                                            webView.findAllAsync(currentHighlightText);
                                        }
                                    }, 500);
                                },
                                throwable -> {
                                    Log.e(TAG, "Error loading HTML to WebView", throwable);
                                }
                        )
        );
    }

    private boolean handleOtherToolbarItems(int itemId) {
        switch (itemId) {
            case R.id.reExtract:
                reExtractArticle();
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
        if (webView == null || isDestroyed) {
            Log.w(TAG, "Cannot adjust text zoom: webView is null or activity destroyed");
            return;
        }
        try {
            int currentZoom = webView.getSettings().getTextZoom();
            int newZoom = zoomIn ? currentZoom + 10 : currentZoom - 10;
            webView.getSettings().setTextZoom(newZoom);
            if (sharedPreferencesRepository != null) {
                sharedPreferencesRepository.setTextZoom(newZoom);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting text zoom", e);
        }
    }

    private void toggleBookmark() {
        if (bookmarkButton == null || isDestroyed) {
            Log.w(TAG, "Cannot toggle bookmark: bookmarkButton is null or activity destroyed");
            return;
        }
        if (bookmark == null || bookmark.equals("N")) {
            bookmarkButton.setIcon(R.drawable.ic_bookmark_filled);
            if (webViewViewModel != null && currentId > 0) {
                webViewViewModel.updateBookmark("Y", currentId);
            }
            bookmark = "Y";
            makeSnackbar("Bookmark Complete");
        } else {
            bookmarkButton.setIcon(R.drawable.ic_bookmark_outline);
            if (webViewViewModel != null && currentId > 0) {
                webViewViewModel.updateBookmark("N", currentId);
            }
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
        if (toolbar == null) {
            Log.e(TAG, "Toolbar is null, cannot initialize listeners");
            return;
        }
        toolbar.setNavigationOnClickListener(view -> onBackPressed());

        toolbar.setOnMenuItemClickListener(item -> {
            if (isDestroyed) return false;
            int itemId = item.getItemId();

            if (itemId == R.id.switchPlayMode) {
                isReadingMode = false;
                if (functionButtonsReadingMode != null) {
                    functionButtonsReadingMode.setVisibility(View.INVISIBLE);
                }
                if (switchPlayModeButton != null) {
                    switchPlayModeButton.setVisible(false);
                }
                if (ttsExtractor != null) {
                    ttsExtractor.setCallback((WebViewListener) null);
                }
                switchPlayMode();
                if (mMediaBrowserHelper != null) {
                    mMediaBrowserHelper.onStart();
                }
                if (functionButtons != null) {
                    functionButtons.setVisibility(View.VISIBLE);
                    functionButtons.setAlpha(1.0f);
                }
                return true;

            } else if (itemId == R.id.switchReadMode) {
                isReadingMode = true;
                if (functionButtons != null) {
                    functionButtons.setVisibility(View.INVISIBLE);
                }
                if (switchReadModeButton != null) {
                    switchReadModeButton.setVisible(false);
                }
                if (ttsPlayer != null) {
                    ttsPlayer.setWebViewCallback(null);
                }
                if (mMediaBrowserHelper != null && mMediaBrowserHelper.getTransportControls() != null) {
                    mMediaBrowserHelper.getTransportControls().stop();
                    mMediaBrowserHelper.onStop();
                }
                if (webView != null) {
                    webView.clearMatches();
                }
                currentHighlightText = null;
                switchReadMode();
                return true;

            } else if (itemId == R.id.highlightText) {
                if (sharedPreferencesRepository == null) return false;
                boolean isHighlight = sharedPreferencesRepository.getHighlightText();
                sharedPreferencesRepository.setHighlightText(!isHighlight);
                if (isHighlight) {
                    if (webView != null) {
                        webView.clearMatches();
                    }
                    currentHighlightText = null;
                    if (highlightTextButton != null) {
                        highlightTextButton.setTitle(R.string.highlight_text_turn_on);
                    }
                    View rootView = findViewById(R.id.webView_view);
                    if (rootView != null) {
                        Snackbar.make(rootView, "Highlight is turned off", Snackbar.LENGTH_SHORT).show();
                    }
                } else {
                    if (highlightTextButton != null) {
                        highlightTextButton.setTitle(R.string.highlight_text_turn_off);
                    }
                    View rootView = findViewById(R.id.webView_view);
                    if (rootView != null) {
                        Snackbar.make(rootView, "Highlight is turned on", Snackbar.LENGTH_SHORT).show();
                    }
                }
                return true;
            }

            return handleOtherToolbarItems(itemId);
        });
    }

    private void initializeWebViewSettings() {
        if (webView == null) {
            Log.e(TAG, "WebView is null, cannot initialize settings");
            return;
        }
        try {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setBuiltInZoomControls(true);
            webView.getSettings().setDisplayZoomControls(false);
            webView.getSettings().setLoadsImagesAutomatically(true);
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

            if (sharedPreferencesRepository != null) {
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
            }

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);

                    if (isDestroyed) return;

                    int ttsProgress = (ttsPlayer != null) ? ttsPlayer.getCurrentExtractProgress() : 100;
                    int combinedProgress = Math.min(newProgress, ttsProgress);

                    if (loading != null) {
                        loading.setVisibility(View.VISIBLE);
                        loading.setProgress(combinedProgress);
                        if (combinedProgress >= 95 && (ttsPlayer == null || !ttsPlayer.isPreparing() || ttsPlayer.ttsIsNull())) {
                            loading.setVisibility(View.GONE);
                        }
                    }

                    if (newProgress == 100 && currentLoadingUrl != null) {
                        checkJavaScriptExecution();
                    }
                }

                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                    if (consoleMessage.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(TAG, "JavaScript Error: " + consoleMessage.message() +
                                " at line " + consoleMessage.lineNumber() +
                                " of " + consoleMessage.sourceId());
                    }
                    return super.onConsoleMessage(consoleMessage);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing WebView settings", e);
        }
    }

    @Override
    public void showFakeLoading() {
        if (isDestroyed) return;
        runOnUiThread(() -> {
            if (isDestroyed || loading == null) return;
            Log.d(TAG, "TTS is preparing, showing fake loading indicator.");
            loading.setProgress(0);
            loading.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void hideFakeLoading() {
        if (isDestroyed) return;
        runOnUiThread(() -> {
            if (isDestroyed || loading == null) return;
            Log.d(TAG, "TTS is ready, hiding fake loading indicator.");
            loading.setVisibility(View.GONE);
        });
    }

    @Override
    public void updateLoadingProgress(int progress) {
        if (isDestroyed) return;
        runOnUiThread(() -> {
            if (isDestroyed || loading == null || ttsPlayer == null) return;
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
        if (isDestroyed) return;
        runOnUiThread(() -> {
            if (isDestroyed) return;
            int ttsProgress = (ttsPlayer != null) ? ttsPlayer.getCurrentExtractProgress() : 100;
            int webProgress = isWebViewContentLoaded ? 100 : (webView != null ? webView.getProgress() : 100);
            int combinedProgress = Math.min(ttsProgress, webProgress);

            if (loading != null) {
                if (combinedProgress >= 100 && (ttsPlayer == null || !ttsPlayer.isPreparing())) {
                    loading.setProgress(100);
                    loading.setVisibility(View.GONE);
                    Log.d(TAG, "[syncLoadingWithTts] Forcibly hid loading.");
                } else {
                    loading.setProgress(combinedProgress);
                    loading.setVisibility(View.VISIBLE);
                    Log.d(TAG, "[syncLoadingWithTts] Still loading... progress = " + combinedProgress);
                }
            }
        });
    }

    private void switchReadMode() {
        if (isDestroyed) return;
        if (functionButtonsReadingMode != null) {
            functionButtonsReadingMode.setVisibility(View.VISIBLE);
        }
        if (fabContainer != null) {
            fabContainer.setVisibility(View.GONE);
        }

        if (webView != null) {
            webView.setWebViewClient(new ReadingWebClient());
        }

        if (binding != null && binding.nextArticleButton != null) {
            binding.nextArticleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (ttsPlaylist != null && ttsPlaylist.skipNext()) {
                        setupReadingWebView();
                    } else {
                        View rootView = findViewById(R.id.webView_view);
                        if (rootView != null) {
                            Snackbar.make(rootView, "This is the last article", Snackbar.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        if (binding != null && binding.previousArticleButton != null) {
            binding.previousArticleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (ttsPlaylist != null && ttsPlaylist.skipPrevious()) {
                        setupReadingWebView();
                    } else {
                        View rootView = findViewById(R.id.webView_view);
                        if (rootView != null) {
                            Snackbar.make(rootView, "This is the first article", Snackbar.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        setupReadingWebView();

        if (ttsPlayer != null) {
            ttsPlayer.setupMediaPlayer(false);
        }

        if (switchPlayModeButton != null) {
            switchPlayModeButton.setVisible(true);
        }
    }

    private void switchPlayMode() {
        if (isDestroyed) return;
        if (webView != null) {
            webView.setWebViewClient(new WebClient());
        }
        if (fabContainer != null) {
            fabContainer.setVisibility(View.VISIBLE);
        }
        setupMediaPlaybackButtons();

        mMediaBrowserHelper = new MediaBrowserConnection(this);
        if (mMediaBrowserHelper != null) {
            mMediaBrowserHelper.registerCallback(new MediaBrowserListener());
        }

        if (switchReadModeButton != null) {
            switchReadModeButton.setVisible(true);
        }
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
                        if (htmlToLoad == null) {
                            throw new Exception("No HTML content available");
                        }
                        return htmlToLoad;
                    })
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    htmlToLoad -> {
                                        if (htmlToLoad != null) {
                                            loadHtmlIntoWebView(htmlToLoad);
                                        } else {
                                            isWaitingForArticleContent = true;
                                            makeSnackbar("Please wait, loading article...");
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
        if (isDestroyed || webView == null || webViewViewModel == null || sharedPreferencesRepository == null) {
            return;
        }
        if (!isReadingMode && sharedPreferencesRepository.getHighlightText()) {
            String text = searchText.trim();
            if (webViewViewModel.endsWithBreak(text)) {
                text = text.substring(0, text.length() - 1);
            }
            Log.d(TAG, "Highlighted text: " + text);
            String finalText = text.trim();
            currentHighlightText = finalText;
            ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> {
                if (webView != null && !isDestroyed) {
                    webView.findAllAsync(finalText);
                }
            });
        }
    }

    @Override
    public void finishedSetup() {
        if (isDestroyed) return;
        ContextCompat.getMainExecutor(getApplicationContext()).execute(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;
                if (!isReadingMode) {
                    syncLoadingWithTts();
                    if (functionButtons != null) {
                        functionButtons.setVisibility(View.VISIBLE);
                        functionButtons.setAlpha(1.0f);
                    }
                }
                if (reloadButton != null) {
                    reloadButton.setVisible(true);
                }
                if (bookmarkButton != null) {
                    bookmarkButton.setVisible(true);
                }
                if (browserButton != null && !sharedPreferencesRepository.getWebViewMode(currentId)) {
                    browserButton.setVisible(true);
                }
                if (highlightTextButton != null) {
                    highlightTextButton.setVisible(true);
                }
                updateToggleTranslationVisibility();
                if (showOfflineButton && offlineButton != null) {
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
        if (isDestroyed) return;
        View rootView = findViewById(R.id.webView_view);
        if (rootView != null && message != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show();
        }
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

    private void reExtractArticle() {
        if (currentId <= 0) {
            Log.w(TAG, "reExtractArticle() aborted: invalid currentId");
            makeSnackbar("Cannot re-extract: Invalid article");
            return;
        }

        Log.d(TAG, "Re-extract triggered for entryId: " + currentId);
        makeSnackbar("Re-extracting article...");

        webViewViewModel.resetEntry(currentId);
        webViewViewModel.clearLiveEntryCache(currentId);

        isTranslatedView = false;
        sharedPreferencesRepository.setIsTranslatedView(currentId, false);

        if (!isReadingMode) {
            mMediaBrowserHelper.getTransportControls().stop();
        }

        Intent intent = getIntent();
        intent.putExtra("entry_id", currentId);
        intent.putExtra("forceOriginal", true);

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
        isDestroyed = true;
        super.onDestroy();

        if (autoTranslationObserver != null && checkAutoTranslated != null) {
            autoTranslationObserver.removeObserver(checkAutoTranslated);
        }

        if (retryHandler != null) {
            retryHandler.removeCallbacksAndMessages(null);
        }

        if (isReadingMode) {
            if (switchPlayModeButton != null) {
                switchPlayModeButton.setVisible(false);
            }
            if (functionButtonsReadingMode != null) {
                functionButtonsReadingMode.setVisibility(View.INVISIBLE);
            }
        } else {
            if (functionButtons != null) {
                functionButtons.setVisibility(View.INVISIBLE);
            }
            if (switchReadModeButton != null) {
                switchReadModeButton.setVisible(false);
            }
        }
        if (reloadButton != null) {
            reloadButton.setVisible(false);
        }
        if (bookmarkButton != null) {
            bookmarkButton.setVisible(false);
        }
        if (highlightTextButton != null) {
            highlightTextButton.setVisible(false);
        }
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
        if (textUtil != null) {
            textUtil.onDestroy();
        }

        if (webView != null) {
            try {
                webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
                webView.clearHistory();
                webView.clearCache(true);
                webView.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error destroying WebView", e);
            } finally {
                webView = null;
            }
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
        if (ttsPlayer != null) {
            ttsPlayer.setWebViewConnected(false);
            ttsPlayer.setUiControlPlayback(false);
        }

        if (webView != null && sharedPreferencesRepository != null) {
            webView.onPause();
            if (currentId != 0) {
                sharedPreferencesRepository.setScrollX(currentId, webView.getScrollX());
                sharedPreferencesRepository.setScrollY(currentId, webView.getScrollY());
                sharedPreferencesRepository.setIsTranslatedView(currentId, isTranslatedView);
                sharedPreferencesRepository.setActiveFab(currentId, activeFab.name());
            }
        }

        if (mMediaBrowserHelper != null) {
            MediaControllerCompat mediaController = mMediaBrowserHelper.getMediaController();
            if (mediaController != null && mediaControllerCallback != null) {
                mediaController.unregisterCallback(mediaControllerCallback);
                Log.d(TAG, "MediaController callback unregistered");
            }
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        if (ttsPlayer != null) {
            ttsPlayer.setWebViewConnected(true);
            updatePlayPauseButtonIcon(ttsPlayer.isSpeaking() && !ttsPlayer.isPausedManually());
            Log.d(TAG, "onResume: isSpeaking=" + ttsPlayer.isSpeaking() + ", isPausedManually=" + ttsPlayer.isPausedManually());
        }

        if (!isReadingMode && mMediaBrowserHelper != null) {
            mMediaBrowserHelper.onStart();
            MediaControllerCompat mediaController = mMediaBrowserHelper.getMediaController();
            if (mediaController != null && mediaControllerCallback != null) {
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
            isWebViewContentLoaded = false;
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
            isWebViewContentLoaded = true;
            webViewViewModel.setLoadingState(false);
            syncLoadingWithTts();
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
            isWebViewContentLoaded = false;
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
            isWebViewContentLoaded = true;
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
                                    if (htmlToLoad == null) {
                                        throw new Exception("No HTML content available");
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

                                                Entry entry = webViewViewModel.getEntryById(currentId);
                                                if (entry != null) {
                                                    boolean hasTranslation = entry.getOriginalHtml() != null && entry.getHtml() != null && !entry.getOriginalHtml().equals(entry.getHtml());

                                                    if (!hasTranslation) {
                                                        if (sharedPreferencesRepository.getAutoTranslateSummary()) {
                                                            setActiveFab(ActiveFab.TRANSLATE_SUMMARY);
                                                            performTranslateAndSummary();
                                                        } else if (sharedPreferencesRepository.getAutoTranslate()) {
                                                            setActiveFab(ActiveFab.TRANSLATE);
                                                            translate();
                                                        }
                                                    }

                                                    loadSavedSummaryOrGenerate();
                                                }
                                            } else {
                                                isWaitingForArticleContent = true;
                                                makeSnackbar("Please wait, loading article...");
                                                webView.loadUrl(currentLink);
                                                Log.d(TAG, "Fallback: loading live URL - " + currentLink);
                                                browserButton.setVisible(true);
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

    /**
     * Check if JavaScript executed successfully by injecting a test script
     */
    private void checkJavaScriptExecution() {
        if (isDestroyed || webView == null || retryHandler == null) {
            return;
        }
        retryHandler.removeCallbacksAndMessages(null);

        retryHandler.postDelayed(() -> {
            if (isDestroyed || webView == null) {
                return;
            }
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
                    if (isDestroyed) return;
                    boolean isPageHealthy = "true".equals(result);
                    Log.d(TAG, "JavaScript execution check: " + (isPageHealthy ? "SUCCESS" : "FAILED") + ", result=" + result);

                    if (!isPageHealthy) {
                        handlePageLoadFailure(currentLoadingUrl, "JavaScript execution failed or incomplete page load");
                    } else {
                        pageLoadRetryCount = 0;
                    }
                }
            );
        }, 1000);
    }

    /**
     * Handle page load failures with automatic retry or user prompt
     */
    private void handlePageLoadFailure(String failedUrl, String errorMessage) {
        if (isDestroyed) {
            return;
        }
        if (failedUrl == null || !failedUrl.equals(currentLoadingUrl)) {
            return;
        }

        runOnUiThread(() -> {
            if (isDestroyed) return;
            if (loading != null) {
                loading.setVisibility(View.GONE);
            }

            if (pageLoadRetryCount < MAX_RETRY_ATTEMPTS) {
                pageLoadRetryCount++;
                Log.w(TAG, "Auto-retry attempt " + pageLoadRetryCount + "/" + MAX_RETRY_ATTEMPTS + " for URL: " + failedUrl);

                makeSnackbar("Page failed to load. Retrying (" + pageLoadRetryCount + "/" + MAX_RETRY_ATTEMPTS + ")...");

                if (retryHandler != null && webView != null) {
                    retryHandler.postDelayed(() -> {
                        if (!isDestroyed && webView != null) {
                            webView.reload();
                        }
                    }, 2000);
                }
            } else {
                Log.e(TAG, "Max retry attempts reached for URL: " + failedUrl);
                showRetryDialog(failedUrl, errorMessage);
            }
        });
    }

    /**
     * Show dialog to prompt user to retry or cancel
     */
    private void showRetryDialog(String url, String errorMessage) {
        if (isDestroyed || webView == null) {
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.page_load_failed))
            .setMessage("The page failed to load correctly after " + MAX_RETRY_ATTEMPTS + " attempts.\n\n" +
                       "Error: " + errorMessage + "\n\n" +
                       "Would you like to retry loading the page?")
            .setPositiveButton("Retry", (dialog, which) -> {
                if (!isDestroyed && webView != null) {
                    pageLoadRetryCount = 0;
                    webView.reload();
                }
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

    private void updatePlayPauseButtonIcon(boolean playing) {
        if (playPauseButton == null || isDestroyed) {
            return;
        }
        int iconRes = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        try {
            playPauseButton.setIcon(ContextCompat.getDrawable(this, iconRes));
        } catch (Exception e) {
            Log.e(TAG, "Error updating play/pause button icon", e);
        }
    }
}

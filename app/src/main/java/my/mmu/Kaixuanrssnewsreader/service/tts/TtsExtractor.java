package my.mmu.Kaixuanrssnewsreader.service.tts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.ContextCompat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import my.mmu.Kaixuanrssnewsreader.data.entry.Entry;
import my.mmu.Kaixuanrssnewsreader.data.entry.EntryRepository;
import my.mmu.Kaixuanrssnewsreader.data.feed.FeedRepository;
import my.mmu.Kaixuanrssnewsreader.data.playlist.PlaylistRepository;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.service.util.TextUtil;
import my.mmu.Kaixuanrssnewsreader.ui.webview.WebViewListener;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.extended.Readability4JExtended;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class TtsExtractor {

    private final String TAG = TtsExtractor.class.getSimpleName();
    private String currentLanguage;
    private boolean isLockedByTtsPlayer = false;
    private final Context context;
    private final EntryRepository entryRepository;
    private final FeedRepository feedRepository;
    private final PlaylistRepository playlistRepository;
    private final TextUtil textUtil;
    private final SharedPreferencesRepository sharedPreferencesRepository;
    private WebView webView;
    private String currentLink;
    private String currentTitle;
    private long currentIdInProgress;
    private boolean extractionInProgress;
    private int delayTime;
    private TtsPlayerListener ttsCallback;
    private TtsPlaylist ttsPlaylist;
    private WebViewListener webViewCallback;
    private Date playlistDate;
    public final String delimiter = "--####--";
    private final List<Long> failedIds = new ArrayList<>();
    private final HashMap<Long, Integer> retryCountMap = new HashMap<>();
    private final int MAX_RETRIES = 5;
    private long lastExtractStart = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Inject
    public TtsExtractor(@ApplicationContext Context context, TtsPlaylist ttsPlaylist, EntryRepository entryRepository, FeedRepository feedRepository, PlaylistRepository playlistRepository, TextUtil textUtil, SharedPreferencesRepository sharedPreferencesRepository) {
        this.context = context;
        this.ttsPlaylist = ttsPlaylist;
        this.entryRepository = entryRepository;
        this.feedRepository = feedRepository;
        this.playlistRepository = playlistRepository;
        this.textUtil = textUtil;
        this.sharedPreferencesRepository = sharedPreferencesRepository;

        ContextCompat.getMainExecutor(context).execute(new Runnable() {
            @Override
            public void run() {
                webView = new WebView(context);
                webView.setWebViewClient(new WebClient());
                webView.clearCache(true);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setDomStorageEnabled(true);
            }
        });
    }

    public void extractAllEntries() {
        Log.d(TAG, "extractAllEntries called | extractionInProgress = " + extractionInProgress);

        if (extractionInProgress && currentIdInProgress == -1) {
            Log.w(TAG, "Recovery: extractionInProgress = true but currentIdInProgress == -1 → Resetting flag.");
            extractionInProgress = false;
        }

        Entry entry = entryRepository.getEmptyContentEntry();

        if (entry == null && !failedIds.isEmpty()) {
            long retryId = failedIds.remove(0);
            int attempts = retryCountMap.getOrDefault(retryId, 0);

            if (attempts < MAX_RETRIES) {
                retryCountMap.put(retryId, attempts + 1);
                Log.d(TAG, "Retrying failed article ID: " + retryId + " | Attempt " + (attempts + 1));
                entry = entryRepository.getEntryById(retryId);
            } else {
                Log.w(TAG, "Max retries reached for article ID: " + retryId);
                extractAllEntries();
                return;
            }
        }

        if (entry != null) {
            Log.d(TAG, "Next entry: id=" + entry.getId() + ", title=" + entry.getTitle() + ", priority=" + entry.getPriority());
            if (!extractionInProgress) {
                Log.d(TAG, "extracting...");
                extractionInProgress = true;
                currentIdInProgress = entry.getId();
                currentLink = entry.getLink();
                currentTitle = entry.getTitle();
                delayTime = feedRepository.getDelayTimeById(entry.getFeedId());
                ContextCompat.getMainExecutor(context).execute(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl(currentLink);
                        Log.d("Test url",currentLink);
                    }
                });
                lastExtractStart = System.currentTimeMillis();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (extractionInProgress && System.currentTimeMillis() - lastExtractStart > 30000) {
                        Log.w(TAG, "[Timeout] Extraction stuck >30s, resetting manually");
                        failedIds.add(currentIdInProgress);
                        currentIdInProgress = -1;
                        extractionInProgress = false;
                        extractAllEntries();
                    }
                }, 30000);
            }
        }else {
            Log.d(TAG, "No entry returned by getEmptyContentEntry()");

            if (failedIds.isEmpty()) {
                scheduleRetryForFailedEntries();
            }
        }
    }

    private void translateHtml(String html, String content, final long currentIdInProgress, String currentTitle) {
        String sourceLanguage = textUtil.identifyLanguageRx(content).blockingGet();
        String targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();
        setCurrentLanguage(targetLanguage, false);

        if (!sourceLanguage.equals(targetLanguage)) {
            Log.d(TAG, "translateHtml: translating from " + sourceLanguage + " to " + targetLanguage);

            String method = sharedPreferencesRepository.getTranslationMethod();
            Single<String> translationSingle;

            if ("lineByLine".equalsIgnoreCase(method)) {
                translationSingle = textUtil.translateHtmlLineByLine(
                        sourceLanguage,
                        targetLanguage,
                        html,
                        currentTitle,
                        currentIdInProgress
                );
            } else if ("paragraphByParagraph".equalsIgnoreCase(method)) {
                translationSingle = textUtil.translateHtmlByParagraph(
                        sourceLanguage,
                        targetLanguage,
                        html,
                        currentTitle,
                        currentIdInProgress,
                        progress -> {}
                );
            } else {
                translationSingle = textUtil.translateHtmlAllAtOnce(
                        sourceLanguage,
                        targetLanguage,
                        html,
                        currentTitle,
                        currentIdInProgress,
                        progress -> {}
                );
            }

            translationSingle
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            translatedHtml -> {
                                entryRepository.updateHtml(translatedHtml, currentIdInProgress);
                                String translatedContent = textUtil.extractHtmlContent(translatedHtml, delimiter);
                                entryRepository.updateTranslatedText(translatedContent, currentIdInProgress);
                                entryRepository.updateTranslated(translatedContent, currentIdInProgress);
                                setCurrentLanguage(targetLanguage, true);

                                if (!sharedPreferencesRepository.hasTranslationToggle(currentIdInProgress)) {
                                    sharedPreferencesRepository.setIsTranslatedView(currentIdInProgress, true);
                                    Log.d(TAG, "[translateHtml] Defaulting to translated view since this is first-time translation.");
                                }

                                Log.d(TAG, "translateHtml: translation completed and saved");
                            },
                            throwable -> {
                                Log.e(TAG, "translateHtml: error translating", throwable);
                                failedIds.add(currentIdInProgress);
                            }
                    );
        }
    }

    public void setCallback(TtsPlayerListener callback) {
        this.ttsCallback = callback;
    }

    public void setCallback(WebViewListener callback) {
        this.webViewCallback = callback;
    }

    public void prioritize() {
        Date newPlaylistDate = playlistRepository.getLatestPlaylistCreatedDate();

        if (playlistDate == null || !playlistDate.equals(newPlaylistDate)) {
            playlistDate = newPlaylistDate;
            entryRepository.clearPriority();
            List<Long> playlist = stringToLongList(playlistRepository.getLatestPlaylist());
            long lastId = entryRepository.getLastVisitedEntryId();
            int index = playlist.indexOf(lastId);
            int priority = 1;
            entryRepository.updatePriority(priority, lastId);

            boolean loop = true;
            while (loop) {
                index += 1;
                priority += 1;
                if (index < playlist.size()) {
                    long id = playlist.get(index);
                    entryRepository.updatePriority(priority, id);
                } else {
                    loop = false;
                }
            }
        }
        extractAllEntries();
    }

    public class WebClient extends WebViewClient {

        private final Handler handler = new Handler();

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "[onPageFinished] triggered for: " + url);
            super.onPageFinished(view, url);
            if (extractionInProgress && webView.getProgress() == 100) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("(function() {return document.getElementsByTagName('html')[0].outerHTML;})();", new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(final String value) {
                                Log.d(TAG, "Receiving value...");
                                Single.fromCallable(() -> {
                                    JsonReader reader = new JsonReader(new StringReader(value));
                                    reader.setLenient(true);
                                    boolean stopExtracting = false;
                                    boolean success = false;
                                    StringBuilder content = new StringBuilder();

                                    try {
                                        if (reader.peek() == JsonToken.STRING) {
                                            String html = reader.nextString();
                                            if (html != null) {
                                                 Readability4JExtended readability4J = new Readability4JExtended(currentLink, html);
                                                 Article article = readability4J.parse();

                                                 Date extractedPublishedDate = extractDateFromHtml(html);

                                                 boolean hasBodyContent = false;

                                                 if (article.getContentWithUtf8Encoding() != null) {
                                                     Document doc = Jsoup.parse(article.getContentWithUtf8Encoding());
                                                     doc.select("img").removeAttr("width");
                                                     doc.select("img").removeAttr("height");
                                                     doc.select("img").removeAttr("sizes");
                                                     doc.select("img").removeAttr("srcset");
                                                     doc.select("h1").remove();
                                                     doc.select("img").attr("style", "border-radius: 5px; width: 100%; margin-left:0");
                                                     doc.select("figure").attr("style", "width: 100%; margin-left:0");
                                                     doc.select("iframe").attr("style", "width: 100%; margin-left:0");

                                                     List<String> tags = Arrays.asList("h2", "h3", "h4", "h5", "h6", "p", "td", "pre", "th", "li", "figcaption", "blockquote", "section");
                                                     for (Element element : doc.getAllElements()) {
                                                         if (tags.contains(element.tagName())) {
                                                             boolean sameContent = false;
                                                             for (Element child : element.children()) {
                                                                 if (tags.contains(child.tagName())) {
                                                                     sameContent = true;
                                                                 }
                                                             }
                                                             if (!sameContent) {
                                                                 String text = element.text().trim();
                                                                 if (!text.isEmpty() && text.length() > 1) {
                                                                     hasBodyContent = true;
                                                                     if (currentTitle != null && !currentTitle.isEmpty()) {
                                                                         content.append(delimiter).append(text);
                                                                     } else {
                                                                         content.append(text);
                                                                     }
                                                                 } else {
                                                                     element.remove();
                                                                 }
                                                             }
                                                         }
                                                     }

                                                    if (hasBodyContent) {
                                                        entryRepository.updateHtml(doc.html(), currentIdInProgress);

                                                        if (extractedPublishedDate != null) {
                                                            entryRepository.updatePublishedDate(extractedPublishedDate, currentIdInProgress);
                                                            Log.d(TAG, "Updated publishedDate for entry " + currentIdInProgress + " to " + extractedPublishedDate);
                                                        }

                                                        if (entryRepository.getOriginalHtmlById(currentIdInProgress) == null) {
                                                             entryRepository.updateOriginalHtml(doc.html(), currentIdInProgress);
                                                             if (currentTitle != null && !currentTitle.isEmpty()) {
                                                                 content.insert(0, currentTitle);
                                                             }
                                                             entryRepository.updateContent(content.toString(), currentIdInProgress);
                                                         }

                                                         if (sharedPreferencesRepository.getAutoTranslate()) {
                                                             translateHtml(doc.html(), content.toString(), currentIdInProgress, currentTitle);
                                                         }

                                                         success = true;
                                                     } else {
                                                         stopExtracting = true;
                                                     }
                                                 }
                                            }
                                        }
                                    } catch (Exception e) {
                                        return new Object[]{false, content.toString(), false, e};
                                    }
                                    return new Object[]{stopExtracting, content.toString(), success, null};
                                })
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(results -> {
                                    boolean stopExtracting = (boolean) results[0];
                                    String contentStr = (String) results[1];
                                    boolean success = (boolean) results[2];
                                    Exception error = (Exception) results[3];

                                    if (error != null) {
                                        Log.e(TAG, "[onReceiveValue] Exception during extraction", error);
                                        failedIds.add(currentIdInProgress);
                                    } else if (success) {
                                        boolean isTranslated = sharedPreferencesRepository.getIsTranslatedView(currentIdInProgress);
                                        if (currentIdInProgress == ttsPlaylist.getPlayingId()) {
                                            if (ttsCallback != null) {
                                                String lang = currentLanguage != null ? currentLanguage : "en";
                                                Entry entry = entryRepository.getEntryById(currentIdInProgress);
                                                String contentToRead;
                                                if (isTranslated && entry != null && entry.getTranslated() != null && !entry.getTranslated().trim().isEmpty()) {
                                                    contentToRead = entry.getTranslated();
                                                    Log.d(TAG, "[TtsExtractor] Using translated content for TTS");
                                                } else {
                                                    contentToRead = entry != null ? entry.getContent() : "";
                                                    Log.d(TAG, "[TtsExtractor] Using original content for TTS");
                                                }
                                                ttsCallback.extractToTts(contentToRead, lang);
                                                ttsCallback = null;
                                            }
                                        }
                                    } else {
                                        if (!stopExtracting) {
                                            if (webViewCallback != null) {
                                                webViewCallback.makeSnackbar("Failed to retrieve the html");
                                            }
                                            Log.d(TAG, "No html found!");
                                        }
                                    }

                                    if (stopExtracting || (contentStr.isEmpty() && !success && error == null)) {
                                        Log.w(TAG, "Extraction failed for ID: " + currentIdInProgress);
                                        if (!failedIds.contains(currentIdInProgress)) {
                                            failedIds.add(currentIdInProgress);
                                        }
                                    }

                                    if (webViewCallback != null) {
                                        Log.d(TAG, "Extraction complete. Notifying UI via finishedSetup()");
                                        webViewCallback.finishedSetup();
                                        webViewCallback = null;
                                    }

                                    Log.d(TAG, "[onReceiveValue] Extraction completed for ID: " + currentIdInProgress);
                                    currentIdInProgress = -1;
                                    extractionInProgress = false;
                                    extractAllEntries();

                                }, throwable -> {
                                    Log.e(TAG, "RxJava error during extraction", throwable);
                                    failedIds.add(currentIdInProgress);
                                    currentIdInProgress = -1;
                                    extractionInProgress = false;
                                    extractAllEntries();
                                });
                            }
                        });
                    }
                }, delayTime * 1000L);
            } else {
                Log.d(TAG, "loading WebView");
            }
        }
    }

    public void setCurrentLanguage(String lang, boolean lock) {
        Log.d("TtsExtractor", "[setCurrentLanguage] REQUESTED lang = " + lang + ", lock = " + lock + " | current = " + currentLanguage + ", isLocked = " + isLockedByTtsPlayer);

        Log.d("TtsExtractor", Log.getStackTraceString(new Throwable()));

        if (!isLockedByTtsPlayer || lock) {
            Log.d("TtsExtractor", "Language set to: " + lang + " | lock=" + lock);
            this.currentLanguage = lang;
            isLockedByTtsPlayer = lock;
        } else {
            Log.d("TtsExtractor", "Ignored language override to: " + lang + " due to lock");
        }

        Log.d("TtsExtractor", "Language set to: " + lang + " | lock=" + lock + " | isLocked=" + isLockedByTtsPlayer);
    }

    public List<Long> stringToLongList(String genreIds) {
        List<Long> list = new ArrayList<>();

        String[] array = genreIds.split(",");

        for (String s : array) {
            if (!s.isEmpty()) {
                list.add(Long.parseLong(s));
            }
        }
        return list;
    }

    public boolean isLocked() {
        return isLockedByTtsPlayer;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    private Date extractDateFromHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        try {
            Document doc = Jsoup.parse(html);

            String[] dateSelectors = {
                "meta[property=article:published_time]",
                "meta[property=og:article:published_time]",
                "meta[name=published]",
                "meta[name=date]",
                "meta[name=pubdate]",
                "meta[name=publish-date]",
                "meta[name=publishdate]",
                "meta[itemprop=datePublished]",
                "meta[name=dc.date]",
                "meta[name=DC.date]",
                "meta[name=DC.date.issued]",
                "time[datetime]"
            };

            for (String selector : dateSelectors) {
                Element el = doc.selectFirst(selector);
                if (el != null) {
                    String dateStr = el.tagName().equals("time")
                            ? el.attr("datetime")
                            : el.attr("content");
                    if (dateStr != null && !dateStr.trim().isEmpty()) {
                        Date parsed = parseDate(dateStr.trim());
                        if (parsed != null) return parsed;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract date from HTML", e);
        }
        return null;
    }

    private Date parseDate(String dateStr) {
        String[] formats = {
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss Z",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "MMMM d, yyyy",
            "MMM d, yyyy",
            "dd MMM yyyy HH:mm:ss",
            "dd MMM yyyy",
            "dd MMMM yyyy HH:mm:ss",
            "dd MMMM yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.ENGLISH);
                sdf.setLenient(false);
                Date parsed = sdf.parse(dateStr);
                if (parsed != null) return parsed;
            } catch (ParseException e) {
                continue;
            }
        }
        return null;
    }

    private void scheduleRetryForFailedEntries() {
        List<Long> failedEntryIds = entryRepository.getFailedEntryIds();
        if (failedEntryIds.isEmpty()) {
            Log.d(TAG, "No failed entries to retry");
            return;
        }

        int delaySeconds = sharedPreferencesRepository.getExtractionRetryDelay();
        if (delaySeconds <= 0) {
            Log.d(TAG, "Retry delay is 0, not retrying failed entries");
            return;
        }

        Log.d(TAG, "Scheduling retry for " + failedEntryIds.size() + " failed entries in " + delaySeconds + "s");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Retrying failed entries");
            entryRepository.requeueMissingEntries();
            for (Long id : failedEntryIds) {
                if (!failedIds.contains(id)) {
                    failedIds.add(id);
                }
            }
            extractAllEntries();
        }, delaySeconds * 1000L);
    }
}
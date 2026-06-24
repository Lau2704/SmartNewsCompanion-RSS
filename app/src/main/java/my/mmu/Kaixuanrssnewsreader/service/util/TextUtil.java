package my.mmu.Kaixuanrssnewsreader.service.util;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.model.ApiKeyEntry;
import my.mmu.Kaixuanrssnewsreader.model.ApiProvider;
import my.mmu.Kaixuanrssnewsreader.service.util.LocalLlmEngine;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TextUtil {
    public static final String TAG = TextUtil.class.getSimpleName();
    private final CompositeDisposable compositeDisposable;
    private final SharedPreferencesRepository sharedPreferencesRepository;
    private final LocalLlmEngine localLlmEngine;
    private final OkHttpClient client;

    @Inject
    public TextUtil(SharedPreferencesRepository sharedPreferencesRepository, LocalLlmEngine localLlmEngine) {
        this.sharedPreferencesRepository = sharedPreferencesRepository;
        this.localLlmEngine = localLlmEngine;
        compositeDisposable = new CompositeDisposable();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String extractHtmlContent(String html, String delimiter) {
        if (html == null) {
            return "";
        }

        Document doc = Jsoup.parse(html);
        StringBuilder content = new StringBuilder();

        // Using CSS selector to directly access the required elements
        String cssQuery = "h2, h3, h4, h5, h6, p, td, pre, th, li, figcaption, blockquote, section";
        Elements elements = doc.select(cssQuery);

        // Iterate over the selected elements and append them to the StringBuilder
        for (Element element : elements) {
            content.append(element.text());
            content.append(delimiter);  // Append the delimiter after each element's text
        }

        return content.toString().trim();  // Return the trimmed result to remove the last delimiter
    }

    // Translate text element by element
    // Pro: Preserves the HTML structure of the text (e.g. <h1> remains <h1>, <h2> remains <h2>, <p> remains <p>)
    // Con: Slower performance (e.g. translating a very long content (198 elements) can take up to 5 minutes.
    //        In contrast, using the translateAllAtOnce method reduces this time to 2 minutes).
    // Note: Specifying maxConcurrency in x.flatMap (tried with 10 and 100) showed no noticeable difference in performance
    //        compared to leaving it unspecified.
    public Single<String> translateHtmlLineByLine(String sourceLanguage, String targetLanguage, String html, String title, long articleId, Consumer<Integer> progressCallback) {
        Log.d(TAG, "translateHtmlLineByLine: from " + sourceLanguage + " to " + targetLanguage);
        return Single.create(emitter -> {
            try {
                // First, translate the title
                translateText(sourceLanguage, targetLanguage, title)
                        .flatMap(translatedTitle -> {
                            // Parse the HTML
                            Document document = Jsoup.parse(html);
                            // List of tags to extract text from
                            List<String> tags = Arrays.asList("h2", "h3", "h4", "h5", "h6", "p", "td", "pre", "th", "li", "figcaption", "blockquote", "section");
                            // Get all elements with the specified tags
                            Elements elements = document.select(String.join(",", tags));

                            // Check if the translated title has already been prepended
                            Element existingTitleElement = document.select("p.translated-title").first();
                            if (existingTitleElement == null) {
                                Element titleParagraph = new Element(Tag.valueOf("p"), "");
                                titleParagraph.text(translatedTitle);
                                titleParagraph.addClass("translated-title");
                                titleParagraph.attr("data-article-id", String.valueOf(articleId));
                                document.body().prependChild(titleParagraph);
                            }

                            AtomicInteger translatedElements = new AtomicInteger(0);
                            // Create a Flowable from the elements
                            return Flowable.fromIterable(elements)
                                    .flatMapMaybe(element -> {
                                        if (element.hasText()) {
                                            return translateText(sourceLanguage, targetLanguage, element.text())
                                                    .map(translatedText -> {
                                                        element.text(translatedText);
                                                        return translatedText;
                                                    })
                                                    .toMaybe();
                                        }
                                        return Maybe.empty();
                                    })
                                    .doOnNext(translatedText -> {
                                        // Emit progress update
                                        int progress = (int) (100.0 * (translatedElements.incrementAndGet()) / elements.size());
                                        progressCallback.accept(progress);
                                    })
                                    .toList()
                                    .map(ignored -> document.outerHtml());
                        })
                        .subscribe(
                                emitter::onSuccess,
                                emitter::onError
                        );
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    public Single<String> translateHtmlLineByLine(String sourceLanguage, String targetLanguage, String html, String title, long articleId) {
        Log.d(TAG, "translateHtmlLineByLine: from " + sourceLanguage + " to " + targetLanguage);
        return Single.create(emitter -> {
            try {
                // First, translate the title
                translateText(sourceLanguage, targetLanguage, title)
                        .flatMap(translatedTitle -> {
                            // Parse the HTML
                            Document document = Jsoup.parse(html);
                            // List of tags to extract text from
                            List<String> tags = Arrays.asList("h2", "h3", "h4", "h5", "h6", "p", "td", "pre", "th", "li", "figcaption", "blockquote", "section");
                            // Get all elements with the specified tags
                            Elements elements = document.select(String.join(",", tags));

                            Element existingTitleElement = document.select("p.translated-title").first();
                            if (existingTitleElement == null) {
                                Element titleParagraph = new Element(Tag.valueOf("p"), "");
                                titleParagraph.text(translatedTitle);
                                titleParagraph.addClass("translated-title");
                                titleParagraph.attr("data-article-id", String.valueOf(articleId));
                                document.body().prependChild(titleParagraph);
                            }

                            // Create a Flowable from the elements
                            return Flowable.fromIterable(elements)
                                    .flatMapMaybe(element -> {
                                        if (element.hasText()) {
                                            return translateText(sourceLanguage, targetLanguage, element.text()).map(translateText -> {
                                                element.text(translateText);
                                                return translateText;
                                            }).toMaybe();
                                        }
                                        return Maybe.empty();
                                    })
                                    .toList()
                                    .map(ignored -> document.outerHtml());
                        })
                        .subscribe(
                                emitter::onSuccess,
                                emitter::onError
                        );
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }


    // Translation Method: Concat texts from all the elements then translate the concatenated text
    // Pro: Faster performance. (e.g. translating very long content (198 elements) takes only 2 minutes,
    //      compared to 5 minutes with the translateLineByLine method.
    //      (However, it's worth noting that despite being faster, this method's speed is still limited due to the MLKit Model's lack of optimization for long text. The speed of translation also heavily depends on the device specifications, with devices having more memory typically performing faster due to the use of TensorFlow as the backbone.)
    // Con:
    // 1. Special tags are replaced with <p>. Attempting to retain original tags such as <h1> or <h2> often results in errors due to discrepancies in
    //    element count after splitting the translated string using a delimiter (e.g. totalTextToTranslate: 198, totalTranslatedTexts: 210).
    //    Therefore, all tags are replaced with <p>.
    // 2. Due to limitations in the MLKit API translation model, it may not accurately separate lines, resulting in some text remaining untranslated.
    // Note:
    // 1. The delimiter used for splitting can also be translated (e.g., original delimiter ===@@@=== might become ==@== or @@ after translation). Therefore, a regex is used to split the translated text.
    // 2. The choice of delimiter can affect the translation. After testing various options like <br>, &nbsp;, and other character combinations, "++++++@@@@@@++++++" gave the best results.
    // 3. The accuracy of translation can sometimes be compromised, resulting in unusual or unexpected translations.
    // 4. MLKit uses English as an intermediate language for translation. For example, when translating from Chinese to Malay, the process is actually Chinese -> English -> Malay. This indirect translation process may affect the quality of the final translation.
    public Single<String> translateHtmlAllAtOnce(String sourceLanguage, String targetLanguage, String html, String title, long articleId, Consumer<Integer> progressCallback) {
        Log.d(TAG, "translateHtmlAllAtOnce: from " + sourceLanguage + " to " + targetLanguage);
        return Single.create(emitter -> {
            try {
                // First, translate the title
                translateText(sourceLanguage, targetLanguage, title)
                        .flatMap(translatedTitle -> {
                            // Parse the HTML
                            Document document = Jsoup.parse(html);
                            // List of tags to extract text from
                            List<String> tags = Arrays.asList("h2", "h3", "h4", "h5", "h6", "p", "td", "pre", "th", "li", "figcaption", "blockquote", "section");
                            // Get all elements with the specified tags
                            Elements elements = document.select(String.join(",", tags));

                            // Check if the translated title has already been prepended
                            Element existingTitleElement = document.select("p.translated-title").first();
                            if (existingTitleElement == null) {
                                Element titleParagraph = new Element(Tag.valueOf("p"), "");
                                titleParagraph.text(translatedTitle);
                                titleParagraph.addClass("translated-title");
                                titleParagraph.attr("data-article-id", String.valueOf(articleId));
                                document.body().prependChild(titleParagraph);
                            }

                            // Unique delimiter
                            String delimiter = "++++++@@@@@@++++++";
                            // Concatenate all the text
                            StringBuilder stringBuilder = new StringBuilder();
                            for (Element element : elements) {
                                String text = element.text();
                                stringBuilder.append(text);
                                stringBuilder.append(delimiter);
                            }
                            Log.d(TAG, "translateHtml: translating " + elements.size() + " elements");
                            if (elements.isEmpty()) {
                                emitter.onSuccess(document.outerHtml());
                                return Single.just(document.outerHtml());
                            }

                            AtomicInteger progress = new AtomicInteger(0);
                            Thread progressThread = new Thread(() -> {
                                try {
                                    while (progress.get() < 90) {
                                        Thread.sleep(300);
                                        try {
                                            progressCallback.accept(progress.incrementAndGet());
                                        } catch (Throwable callbackException) {
                                            Log.e(TAG, "Progress callback failed", callbackException);
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt(); // Restore interrupted status
                                }
                            });
                            progressThread.start();

                            String combinedText = stringBuilder.toString();
                            return translateText(sourceLanguage, targetLanguage, combinedText)
                                    .map(translatedText -> {
                                        progressThread.interrupt(); // Stop progress simulation
                                        try {
                                            progressCallback.accept(100); // Finalize progress
                                        } catch (Throwable callbackException) {
                                            Log.e(TAG, "Progress callback failed on completion", callbackException);
                                        }
                                        Log.d(TAG, "translateHtml: translatedText: " + translatedText);
                                        String[] translatedTexts = translatedText.split("((\\+ *){1,} *(@ *)* *(\\+ *){1,})|((\\+ *)* *(@ *){2,} *(\\+ *)*)");
                                        Log.d(TAG, "translateHtml: totalTextstoTranslate: " + elements.size() + ", totalTranslatedTexts: " + translatedTexts.length);
                                        
                                        // If total translatedTexts is less or equal than the total elements, replace the text then remove additional elements
                                        if (translatedTexts.length <= elements.size()) {
                                            for (int i = 0; i < translatedTexts.length; i++) {
                                                Element originalElement = elements.get(i);
                                                originalElement.text(translatedTexts[i]);
                                            }
                                            // Remove extra elements from the DOM
                                            for (int i = translatedTexts.length; i < elements.size(); i++) {
                                                Element element = elements.get(i);
                                                Node parent = element.parentNode();
                                                if (parent != null && element.siblingIndex() < parent.childNodeSize()
                                                        && parent.childNode(element.siblingIndex()) == element) {
                                                    element.remove();
                                                }
                                            }
                                        }
                                        // If total translatedTexts is more than the total elements, add additional elements
                                        else {
                                            for (int i = 0; i < elements.size(); i++) {
                                                Element originalElement = elements.get(i);
                                                originalElement.text(translatedTexts[i]);
                                            }
                                            
                                            Element lastElement = elements.last();
                                            for (int i = elements.size(); i < translatedTexts.length; i++) {
                                                Element newElement = new Element(Tag.valueOf("p"), "");
                                                newElement.text(translatedTexts[i]);
                                                if (lastElement != null) {
                                                    lastElement.after(newElement);
                                                    lastElement = newElement; // Update reference for next insertion
                                                }
                                            }
                                        }
                                        return document.outerHtml();
                                    });
                        })
                        .subscribe(
                                emitter::onSuccess,
                                error -> {
                                    try {
                                        progressCallback.accept(0); // Reset progress on error
                                    } catch (Throwable callbackException) {
                                        Log.e(TAG, "Progress callback failed on error reset", callbackException);
                                    }
                                    emitter.onError(error);
                });
            } catch (Exception e) {
                emitter.onError(new RuntimeException("An unexpected error occurred during translation.", e));
            }
        });
    }

    public Single<String> translateHtmlByParagraph(String sourceLanguage, String targetLanguage, String html, String title, long articleId, Consumer<Integer> progressCallback) {
        Log.d(TAG, "translateHtmlByParagraph: from " + sourceLanguage + " to " + targetLanguage);
        Log.d(TAG, "translateHtmlByParagraph CALLED");
        return Single.create(emitter -> {
            try {
                translateText(sourceLanguage, targetLanguage, title)
                        .flatMap(translatedTitle -> {
                            Document document = Jsoup.parse(html);
                            List<String> tags = Arrays.asList("p", "section", "blockquote");
                            Elements paragraphs = document.select(String.join(",", tags));
                            Log.d(TAG, "Found " + paragraphs.size() + " paragraphs for translation");

                            Element existingTitleElement = document.select("p.translated-title").first();
                            if (existingTitleElement == null) {
                                Element titleParagraph = new Element(Tag.valueOf("p"), "");
                                titleParagraph.text(translatedTitle);
                                titleParagraph.addClass("translated-title");
                                titleParagraph.attr("data-article-id", String.valueOf(articleId));
                                document.body().prependChild(titleParagraph);
                            }

                            AtomicInteger translatedCount = new AtomicInteger(0);
                            int total = paragraphs.size();

                            return Flowable.fromIterable(paragraphs)
                                    .flatMapMaybe(paragraph -> {
                                        if (paragraph.hasText()) {
                                            return translateText(sourceLanguage, targetLanguage, paragraph.text())
                                                    .map(translatedText -> {
                                                        paragraph.text(translatedText);
                                                        int progress = (int) ((translatedCount.incrementAndGet() / (float) total) * 100);
                                                        try {
                                                            progressCallback.accept(progress);
                                                        } catch (Exception e) {
                                                            Log.e(TAG, "Progress callback failed", e);
                                                        }
                                                        return translatedText;
                                                    }).toMaybe();
                                        }
                                        return Maybe.empty();
                                    })
                                    .toList()
                                    .map(ignored -> document.outerHtml());
                        })
                        .subscribe(
                                emitter::onSuccess,
                                error -> {
                                    Log.e(TAG, "Error during paragraph translation", error);
                                    emitter.onError(error);
                                }
                        );
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in translateHtmlByParagraph", e);
                emitter.onError(e);
            }
        });
    }

    public Single<String> translateText(String sourceLanguage, String targetLanguage, String text) {
        return Single.<String>create(emitter -> {
            if (text == null || text.isEmpty()) {
                emitter.onError(new IllegalArgumentException("Invalid content for translation"));
                return;
            }

            String systemPrompt = "You are a professional translator. Translate the following text from " + sourceLanguage + " to " + targetLanguage + ". IMPORTANT: Return ONLY the translated text. Do not include the original text, any explanations, notes, or any other content.";

            try {
                String result = executeApiCallWithRetry(systemPrompt, text, 4096);
                Log.d(TAG, "Translated text length: " + result.length());
                emitter.onSuccess(result);
            } catch (Exception e) {
                Log.e(TAG, "Translation error", e);
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Single<String> identifyLanguageRx(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) {
            return Single.just("und");
        }

        String sample = sentence.length() > 500 ? sentence.substring(0, 500) : sentence;

        float confidenceThreshold = (float) sharedPreferencesRepository.getConfidenceThreshold() / 100;

        LanguageIdentificationOptions options = new LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(confidenceThreshold)
                .build();

        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient(options);

        return Single.fromCallable(() -> languageIdentifier.identifyLanguage(sample))
                .subscribeOn(Schedulers.io())
                .map(languageCodeTask -> {
                    try {
                        String languageCode = Tasks.await(languageCodeTask);
                        if ("und".equals(languageCode)) {
                            Log.i(TAG, "Primary identification returned 'und', trying possible languages...");
                            String fallback = identifyPossibleLanguages(languageIdentifier, sample);
                            if (fallback != null) {
                                Log.i(TAG, "Fallback identified language: " + fallback);
                                return fallback;
                            }
                            Log.i(TAG, "Unable to identify language.");
                            return "und";
                        } else {
                            Log.i(TAG, "Identified language: " + languageCode);
                            return languageCode;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error identifying language", e);
                        return "und";
                    }
                })
                .onErrorReturnItem("und");
    }

    private String identifyPossibleLanguages(LanguageIdentifier languageIdentifier, String sample) {
        try {
            java.util.List<com.google.mlkit.nl.languageid.IdentifiedLanguage> possible =
                    Tasks.await(languageIdentifier.identifyPossibleLanguages(sample));
            for (com.google.mlkit.nl.languageid.IdentifiedLanguage dl : possible) {
                String code = dl.getLanguageTag();
                float confidence = dl.getConfidence();
                if (!"und".equals(code) && !"en".equals(code)) {
                    Log.d(TAG, "Possible language candidate: " + code + " confidence=" + confidence);
                    return code;
                }
            }
            if (!possible.isEmpty()) {
                String bestCode = possible.get(0).getLanguageTag();
                if (!"und".equals(bestCode)) {
                    Log.d(TAG, "Using best possible language: " + bestCode);
                    return bestCode;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in identifyPossibleLanguages", e);
        }
        return null;
    }

    public Single<String> summarizeText(String text, int targetWords) {
        return Single.<String>create(emitter -> {
            if (text == null || text.isEmpty()) {
                emitter.onError(new IllegalArgumentException("Invalid content for summarization"));
                return;
            }

            String systemPrompt = String.format(
                "You are a professional news editor. Rewrite the following news article into a concise summary article of approximately %d words.\n\n"
                + "Requirements:\n"
                + "- Write in proper article form with a clear, informative headline on the first line\n"
                + "- Use flowing prose organized into 2-3 short paragraphs\n"
                + "- Open with the most important information (who, what, when, where, why)\n"
                + "- Follow with supporting details, context, and key quotes or data\n"
                + "- Close with any significant implications or outcomes\n"
                + "- Write in a neutral, journalistic tone\n"
                + "- Preserve all specific names, numbers, dates, and locations from the original\n"
                + "- Do NOT add any information not present in the original article\n"
                + "- Write in the SAME LANGUAGE as the original article\n\n"
                + "IMPORTANT: Return ONLY the summary article. No labels, prefixes, explanations, or meta-commentary.",
                targetWords
            );

            String contentToSummarize = text.length() > 4000 ? text.substring(0, 4000) + "..." : text;

            try {
                String result = executeApiCallWithRetry(systemPrompt, contentToSummarize, 4096);
                emitter.onSuccess(result);
            } catch (Exception e) {
                Log.e(TAG, "Summarization error", e);
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Single<String> summarizeAndTranslateText(String text, int targetWords, String sourceLanguage, String targetLanguage) {
        return Single.<String>create(emitter -> {
            if (text == null || text.isEmpty()) {
                emitter.onError(new IllegalArgumentException("Invalid content for summarization"));
                return;
            }

            String systemPrompt;
            if (sourceLanguage != null && targetLanguage != null && sourceLanguage.equals(targetLanguage)) {
                systemPrompt = String.format(
                    "You are a professional news editor. Rewrite the following news article into a concise summary article of approximately %d words.\n\n"
                    + "Requirements:\n"
                    + "- Write in proper article form with a clear, informative headline on the first line\n"
                    + "- Use flowing prose organized into 2-3 short paragraphs\n"
                    + "- Open with the most important information (who, what, when, where, why)\n"
                    + "- Follow with supporting details, context, and key quotes or data\n"
                    + "- Close with any significant implications or outcomes\n"
                    + "- Write in a neutral, journalistic tone\n"
                    + "- Preserve all specific names, numbers, dates, and locations from the original\n"
                    + "- Do NOT add any information not present in the original article\n"
                    + "- Write in the SAME LANGUAGE as the original article\n\n"
                    + "IMPORTANT: Return ONLY the summary article. No labels, prefixes, explanations, or meta-commentary.",
                    targetWords
                );
            } else {
                String targetLanguageName = targetLanguage;
                if (targetLanguage != null && !targetLanguage.isEmpty()) {
                    String display = new Locale(targetLanguage).getDisplayLanguage(Locale.ENGLISH);
                    if (display != null && !display.isEmpty() && !display.equalsIgnoreCase(targetLanguage)) {
                        targetLanguageName = display;
                    }
                }
                systemPrompt = String.format(
                    "You are a professional news editor and translator. Summarize the following news article into a concise summary of approximately %d words, written entirely in %s.\n\n"
                    + "Requirements:\n"
                    + "- Write in proper article form with a clear, informative headline on the first line\n"
                    + "- Use flowing prose organized into 2-3 short paragraphs\n"
                    + "- Open with the most important information (who, what, when, where, why)\n"
                    + "- Follow with supporting details, context, and key quotes or data\n"
                    + "- Close with any significant implications or outcomes\n"
                    + "- Write in a neutral, journalistic tone\n"
                    + "- Preserve all specific names, numbers, dates, and locations from the original\n"
                    + "- Do NOT add any information not present in the original article\n"
                    + "- Write the ENTIRE summary in %s, including the headline\n"
                    + "- Do NOT write any part of the summary in the original language\n\n"
                    + "IMPORTANT: Return ONLY the summary article, written completely in %s. No labels, prefixes, explanations, or meta-commentary.",
                    targetWords, targetLanguageName, targetLanguageName, targetLanguageName
                );
            }

            String contentToProcess = text.length() > 4000 ? text.substring(0, 4000) + "..." : text;

            try {
                String result = executeApiCallWithRetry(systemPrompt, contentToProcess, 4096);
                emitter.onSuccess(result);
            } catch (Exception e) {
                Log.e(TAG, "Summarize+Translate error", e);
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public void onDestroy() {
        compositeDisposable.dispose();
    }

    public boolean isLocalModelReady() {
        return localLlmEngine.isModelDownloaded();
    }

    private String executeApiCallWithRetry(String systemPrompt, String userContent, int maxTokens) throws Exception {
        ApiProvider provider = sharedPreferencesRepository.getApiProvider();

        if (provider.isLocal()) {
            if (!localLlmEngine.isModelDownloaded()) {
                throw new IllegalStateException("Local model not downloaded. Please download the model from Settings first.");
            }
            if (!localLlmEngine.isModelLoaded()) {
                localLlmEngine.loadModel();
            }
            return localLlmEngine.complete(systemPrompt, userContent);
        }

        String model = sharedPreferencesRepository.getModel();
        if (model == null || model.isEmpty()) {
            throw new IllegalArgumentException("Model is not configured. Please set a valid model in Settings.");
        }

        String providerKey = provider.getKey();
        List<ApiKeyEntry> allKeys = sharedPreferencesRepository.getApiKeys(providerKey);
        int maxAttempts = allKeys.size();
        if (maxAttempts == 0) {
            throw new IllegalArgumentException("API key is not configured. Please set a valid API key in Settings.");
        }

        Exception lastError = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String apiKey = sharedPreferencesRepository.getActiveApiKey(providerKey);
            if (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here")) {
                if (attempt < maxAttempts - 1) {
                    Log.w(TAG, "Invalid API key, rotating. Attempt " + (attempt + 1) + "/" + maxAttempts);
                    sharedPreferencesRepository.rotateToNextApiKey(providerKey);
                    continue;
                }
                throw new IllegalArgumentException("API key is not configured. Please set a valid API key in Settings.");
            }

            String url = provider.getEndpoint();
            JSONObject requestBody = buildRequestBody(provider, model, systemPrompt, userContent, maxTokens);

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(requestBody.toString(), JSON);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(body);

            addAuthHeaders(requestBuilder, provider, apiKey);
            requestBuilder.addHeader("Content-Type", "application/json");

            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "API success on attempt " + (attempt + 1) + "/" + maxAttempts + ", response length: " + responseBody.length());
                    return parseResponse(provider, responseBody);
                }

                int code = response.code();
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.w(TAG, "API error " + code + " on attempt " + (attempt + 1) + "/" + maxAttempts);

                if (isRetryableCode(code) && attempt < maxAttempts - 1) {
                    Log.w(TAG, "Rotating to next API key for " + providerKey);
                    sharedPreferencesRepository.rotateToNextApiKey(providerKey);
                    lastError = new Exception(buildErrorMessage(provider, code) + "\n\nDetails: " + errorBody);
                    continue;
                }

                throw new Exception(buildErrorMessage(provider, code) + "\n\nDetails: " + errorBody);
            }
        }

        if (lastError != null) throw lastError;
        throw new Exception("All " + maxAttempts + " API keys exhausted for " + providerKey + ". Please wait and try again, or add more keys in Settings.");
    }

    private boolean isRetryableCode(int code) {
        return code == 401 || code == 402 || code == 403 || code == 429;
    }

    private JSONObject buildRequestBody(ApiProvider provider, String model, String systemPrompt, String userContent, int maxTokens) throws Exception {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);

        if (provider.isAnthropicFormat()) {
            requestBody.put("system", systemPrompt);
            requestBody.put("max_tokens", maxTokens);
            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", userContent);
            messages.put(userMessage);
            requestBody.put("messages", messages);
        } else {
            JSONArray messages = new JSONArray();
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.put(systemMessage);
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", userContent);
            messages.put(userMessage);
            requestBody.put("messages", messages);
        }

        return requestBody;
    }

    private void addAuthHeaders(Request.Builder requestBuilder, ApiProvider provider, String apiKey) {
        if (provider.isAnthropicFormat()) {
            requestBuilder.addHeader("x-api-key", apiKey);
            requestBuilder.addHeader("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        if (provider == ApiProvider.OPENROUTER) {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/smartnewscompanion");
            requestBuilder.addHeader("X-Title", "SmartNewsCompanion");
        }
    }

    private String parseResponse(ApiProvider provider, String responseBody) throws Exception {
        JSONObject jsonResponse = new JSONObject(responseBody);

        if (provider.isAnthropicFormat()) {
            JSONArray content = jsonResponse.getJSONArray("content");
            if (content.length() > 0) {
                return content.getJSONObject(0).getString("text");
            }
            throw new Exception("No result found in API response");
        } else {
            JSONArray choices = jsonResponse.getJSONArray("choices");
            if (choices.length() > 0) {
                return choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
            }
            throw new Exception("No result found in API response");
        }
    }

    private String buildErrorMessage(ApiProvider provider, int code) {
        String name = provider.getKey();
        if (code == 401) {
            return "Invalid API key. Please check your " + name + " API key in Settings.";
        } else if (code == 402) {
            return "API quota exceeded for " + name + ". Your plan may have run out of credits.";
        } else if (code == 403) {
            return "Access denied for " + name + ". Your API key may not have permission for this model.";
        } else if (code == 404) {
            return "Invalid model name. Please check the " + name + " model in Settings.";
        } else if (code == 429) {
            return "Rate limit exceeded. Please wait a moment before trying again.";
        } else if (code == 500 || code == 502 || code == 503) {
            return name + " service error. Please try again later.";
        }
        return "API Error: " + code;
    }
}
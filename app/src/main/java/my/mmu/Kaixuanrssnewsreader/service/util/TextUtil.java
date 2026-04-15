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
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TextUtil {
    public static final String TAG = TextUtil.class.getSimpleName();
    private final CompositeDisposable compositeDisposable;
    private final SharedPreferencesRepository sharedPreferencesRepository;
    private final OkHttpClient client;

    @Inject
    public TextUtil(SharedPreferencesRepository sharedPreferencesRepository) {
        this.sharedPreferencesRepository = sharedPreferencesRepository;
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
                                                elements.get(i).remove();
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

            String apiKey = sharedPreferencesRepository.getGroqApiKey();
            if (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here")) {
                emitter.onError(new IllegalArgumentException("Groq API key is not configured. Please set a valid API key in Settings."));
                return;
            }

            String model = sharedPreferencesRepository.getGroqModel();
            if (model == null || model.isEmpty()) {
                emitter.onError(new IllegalArgumentException("Groq model is not configured. Please set a valid model in Settings."));
                return;
            }

            String url = "https://api.groq.com/openai/v1/chat/completions";

            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", model);

                JSONArray messages = new JSONArray();
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are a professional translator. Translate the following text from " + sourceLanguage + " to " + targetLanguage + ". IMPORTANT: Return ONLY the translated text. Do not include the original text, any explanations, notes, or any other content.");
                messages.put(systemMessage);

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", text);
                messages.put(userMessage);

                requestBody.put("messages", messages);

                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(requestBody.toString(), JSON);

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "API Error - Code: " + response.code() + ", Body: " + errorBody);

                        String errorMessage = "";
                        int code = response.code();

                        if (code == 401) {
                            errorMessage = "Invalid API key. Please check your Groq API key in Settings.";
                        } else if (code == 404) {
                            errorMessage = "Invalid model name. Please check the Groq model in Settings.";
                        } else if (code == 429) {
                            errorMessage = "Rate limit exceeded. Please wait a moment before trying again.";
                        } else if (code == 500 || code == 502 || code == 503) {
                            errorMessage = "Groq service error. Please try again later.";
                        } else {
                            errorMessage = "API Error: " + code;
                        }

                        emitter.onError(new Exception(errorMessage + "\n\nDetails: " + errorBody));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Full API Response length: " + responseBody.length());
                    Log.d(TAG, "API Response (first 500 chars): " + responseBody.substring(0, Math.min(500, responseBody.length())));
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        String translatedText = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        Log.d(TAG, "Translated text length: " + translatedText.length());
                        Log.d(TAG, "Translated text (first 200 chars): " + translatedText.substring(0, Math.min(200, translatedText.length())));

                        emitter.onSuccess(translatedText);
                    } else {
                        emitter.onError(new Exception("No translation result found in API response"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Translation error", e);
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public Single<String> identifyLanguageRx(String sentence) {
        float confidenceThreshold = (float) sharedPreferencesRepository.getConfidenceThreshold() / 100;

        LanguageIdentificationOptions options = new LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(confidenceThreshold)
                .build();

        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient(options);

        return Single.fromCallable(() -> languageIdentifier.identifyLanguage(sentence))
                .subscribeOn(Schedulers.io())
                .map(languageCodeTask -> {
                    try {
                        String languageCode = Tasks.await(languageCodeTask);
                        if ("und".equals(languageCode)) {
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

    public Single<String> summarizeText(String text) {
        return Single.<String>create(emitter -> {
            if (text == null || text.isEmpty()) {
                emitter.onError(new IllegalArgumentException("Invalid content for summarization"));
                return;
            }

            String apiKey = sharedPreferencesRepository.getGroqApiKey();
            if (apiKey == null || apiKey.isEmpty() || apiKey.contains("your-api-key-here")) {
                emitter.onError(new IllegalArgumentException("Groq API key is not configured. Please set a valid API key in Settings."));
                return;
            }

            String model = sharedPreferencesRepository.getGroqModel();
            if (model == null || model.isEmpty()) {
                emitter.onError(new IllegalArgumentException("Groq model is not configured. Please set a valid model in Settings."));
                return;
            }

            String url = "https://api.groq.com/openai/v1/chat/completions";

            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", model);

                JSONArray messages = new JSONArray();
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are a professional summarizer. Summarize the following article in a concise and informative way. Focus on the key points and main ideas. IMPORTANT: Return ONLY the summary without any explanations or additional text.");
                messages.put(systemMessage);

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                String contentToSummarize = text.length() > 4000 ? text.substring(0, 4000) + "..." : text;
                userMessage.put("content", contentToSummarize);
                messages.put(userMessage);

                requestBody.put("messages", messages);

                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(requestBody.toString(), JSON);

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "API Error - Code: " + response.code() + ", Body: " + errorBody);

                        String errorMessage = "";
                        int code = response.code();

                        if (code == 401) {
                            errorMessage = "Invalid API key. Please check your Groq API key in Settings.";
                        } else if (code == 404) {
                            errorMessage = "Invalid model name. Please check the Groq model in Settings.";
                        } else if (code == 429) {
                            errorMessage = "Rate limit exceeded. Please wait a moment before trying again.";
                        } else if (code == 500 || code == 502 || code == 503) {
                            errorMessage = "Groq service error. Please try again later.";
                        } else {
                            errorMessage = "API Error: " + code;
                        }

                        emitter.onError(new Exception(errorMessage + "\n\nDetails: " + errorBody));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Summary API Response length: " + responseBody.length());
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        String summary = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        Log.d(TAG, "Summary length: " + summary.length());
                        emitter.onSuccess(summary);
                    } else {
                        emitter.onError(new Exception("No summary found in API response"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Summarization error", e);
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public void onDestroy() {
        compositeDisposable.dispose();
    }
}
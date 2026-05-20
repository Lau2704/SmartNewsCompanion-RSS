
package my.mmu.Kaixuanrssnewsreader.data.sharedpreferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import my.mmu.Kaixuanrssnewsreader.model.ApiProvider;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

public class SharedPreferencesRepository {

    private static final String TAG = "SharedPreferencesRepository";
    private static final String KEY_API_PROVIDER = "apiProvider";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_API_KEY_SETUP_COMPLETED = "api_key_setup_completed";
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private final Context context;
    private static final String KEY_TOGGLE_STATE_PREFIX = "is_translated_view_";
    private static final String KEY_SCROLL_X_PREFIX = "scroll_x_";
    private static final String KEY_SCROLL_Y_PREFIX = "scroll_y_";
    private static final String KEY_WEB_VIEW_MODE = "web_view_mode_";
    private static final String KEY_CURRENT_READING_ENTRY_ID = "current_reading_entry_id";
    private static final String KEY_SUMMARY_PREFIX = "summary_";
    private static final String KEY_SUMMARY_LANG_PREFIX = "summary_lang_";
    private static final String KEY_TRANSLATED_SUMMARY_PREFIX = "translated_summary_";
    private static final String KEY_TRANSLATED_SUMMARY_LANG_PREFIX = "translated_summary_lang_";
    private static final String KEY_ACTIVE_FAB_PREFIX = "active_fab_";

    @Inject
    public SharedPreferencesRepository(@ApplicationContext Context context) {
        this.context = context;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        editor = sharedPreferences.edit();
    }

    public int getJobPeriodic() {
        return Integer.parseInt(sharedPreferences.getString("jobPeriodic", "0"));
    }

    public void setInitialJobPeriodic() {
        editor.putString("jobPeriodic", "360");
        editor.apply();
    }

    public void setJobPeriodic(String jobPeriodic) {
        editor.putString("jobPeriodic", jobPeriodic);
        editor.apply();
    }

    public boolean getNight() {
        return sharedPreferences.getBoolean("night", false);
    }

    public void setNight(boolean isNight) {
        editor.putBoolean("night", isNight);
        editor.apply();
    }

    public boolean getDisplaySummary() {
        return sharedPreferences.getBoolean("displaySummary", true);
    }

    public void setDisplaySummary(boolean displaySummary) {
        editor.putBoolean("displaySummary", displaySummary);
        editor.apply();
    }

    public boolean getHighlightText() {
        return sharedPreferences.getBoolean("highlightText", true);
    }

    public void setHighlightText(boolean highlightText) {
        editor.putBoolean("highlightText", highlightText);
        editor.apply();
    }

    public void setTextZoom(int textZoom) {
        editor.putInt("textZoom", textZoom);
        editor.apply();
    }

    public int getTextZoom() {
        return sharedPreferences.getInt("textZoom", 0);
    }

    public void setSortBy(String sortBy) {
        editor.putString("sortBy", sortBy);
        editor.apply();
    }

    public String getSortBy() {
        return sharedPreferences.getString("sortBy", "oldest");
    }

    public int getConfidenceThreshold() {
        return sharedPreferences.getInt("confidenceThreshold", 50);
    }

    public void setConfidenceThreshold(int confidenceThreshold) {
        editor.putInt("confidenceThreshold", confidenceThreshold);
        editor.apply();
    }

    public boolean getBackgroundMusic() {
        return sharedPreferences.getBoolean("backgroundMusic", false);
    }

    public void setBackgroundMusic(boolean backgroundMusic) {
        editor.putBoolean("backgroundMusic", backgroundMusic);
        editor.apply();
    }

    public String getBackgroundMusicFile() {
        return sharedPreferences.getString("backgroundMusicFile", "default");
    }

    public void setBackgroundMusicFile(String file) {
        editor.putString("backgroundMusicFile", file);
        editor.apply();
    }

    public int getBackgroundMusicVolume() {
        return sharedPreferences.getInt("backgroundMusicVolume", 50);
    }

    public void setBackgroundMusicVolume(int volume) {
        editor.putInt("backgroundMusicVolume", volume);
        editor.apply();
    }

    public int getEntriesLimitPerFeed() {
        return sharedPreferences.getInt("entriesLimitPerFeed", 1000);
    }

    public void setEntriesLimitPerFeed(int limit) {
        editor.putInt("entriesLimitPerFeed", limit);
        editor.apply();
    }

    public boolean getIsPausedManually() {
        return sharedPreferences.getBoolean("isPausedManually", false);
    }

    public void setIsPausedManually(boolean isPaused) {
        editor.putBoolean("isPausedManually", isPaused);
        editor.apply();
    }

    public String getSystemCurrentLanguage(){
        return context.getResources().getConfiguration().getLocales().get(0).getLanguage();
    }
    public String getDefaultTranslationLanguage() {
        return sharedPreferences.getString("defaultTranslationLanguage", getSystemCurrentLanguage());
    }

    public void setDefaultTranslationLanguage(String language) {
        sharedPreferences.edit().putString("defaultTranslationLanguage", language).apply();
    }

    public void initializeDefaultTranslationLanguageOnFirst() {
        if (!sharedPreferences.contains("defaultTranslationLanguage")) {
            editor.putString("defaultTranslationLanguage", getSystemCurrentLanguage());
            editor.apply();
        }
    }


    public String getTranslationMethod() {
        return sharedPreferences.getString("translationMethod", "allAtOnce");
    }

    public void setTranslationMethod(String method) {
        editor.putString("translationMethod", method);
        editor.apply();
    }

    public boolean getAutoTranslate() {
        return sharedPreferences.getBoolean("autoTranslate", false);
    }

    public void setAutoTranslate(boolean autoTranslate) {
        editor.putBoolean("autoTranslate", autoTranslate);
        editor.apply();
    }

    public void setIsTranslatedView(long entryId, boolean isTranslatedView) {
        sharedPreferences.edit()
                .putBoolean(KEY_TOGGLE_STATE_PREFIX + entryId, isTranslatedView)
                .apply();
    }

    public boolean getIsTranslatedView(long entryId) {
        return sharedPreferences.getBoolean(KEY_TOGGLE_STATE_PREFIX + entryId,false);
    }

    public boolean hasTranslationToggle(long entryId) {
        return sharedPreferences.contains(KEY_TOGGLE_STATE_PREFIX + entryId);
    }

    public void setScrollX(long entryId, int value) {
        sharedPreferences.edit().putInt(KEY_SCROLL_X_PREFIX + entryId, value).apply();
    }

    public void setScrollY(long entryId, int value) {
        sharedPreferences.edit().putInt(KEY_SCROLL_Y_PREFIX + entryId, value).apply();
    }

    public int getScrollX(long entryId) {
        return sharedPreferences.getInt(KEY_SCROLL_X_PREFIX + entryId, 0);
    }

    public int getScrollY(long entryId) {
        return sharedPreferences.getInt(KEY_SCROLL_Y_PREFIX + entryId, 0);
    }

    public void setWebViewMode(long entryId, boolean isWebViewMode) {
        sharedPreferences.edit().putBoolean(KEY_WEB_VIEW_MODE + entryId, isWebViewMode).apply();
    }

    public boolean getWebViewMode(long entryId) {
        return sharedPreferences.getBoolean(KEY_WEB_VIEW_MODE + entryId, false); // default to offline mode
    }

    public void setCurrentReadingEntryId(long entryId) {
        sharedPreferences.edit().putLong(KEY_CURRENT_READING_ENTRY_ID, entryId).apply();
    }

    public long getCurrentReadingEntryId() {
        return sharedPreferences.getLong(KEY_CURRENT_READING_ENTRY_ID, -1);
    }

    public String getGroqApiKey() {
        return sharedPreferences.getString("groqApiKey", "");
    }

    public void setGroqApiKey(String apiKey) {
        editor.putString("groqApiKey", apiKey);
        editor.apply();
    }

    public String getGroqModel() {
        return sharedPreferences.getString("groqModel", "");
    }

    public void setGroqModel(String model) {
        editor.putString("groqModel", model);
        editor.apply();
    }

    public ApiProvider getApiProvider() {
        String key = sharedPreferences.getString(KEY_API_PROVIDER, "groq");
        return ApiProvider.fromKey(key);
    }

    public void setApiProvider(ApiProvider provider) {
        editor.putString(KEY_API_PROVIDER, provider.getKey());
        editor.apply();
    }

    public String getApiKey() {
        ApiProvider provider = getApiProvider();
        return sharedPreferences.getString(provider.getApiKeyPreferenceKey(), "");
    }

    public void setApiKey(String apiKey) {
        ApiProvider provider = getApiProvider();
        editor.putString(provider.getApiKeyPreferenceKey(), apiKey);
        editor.apply();
    }

    public String getModel() {
        ApiProvider provider = getApiProvider();
        return sharedPreferences.getString(provider.getModelPreferenceKey(), "");
    }

    public void setModel(String model) {
        ApiProvider provider = getApiProvider();
        editor.putString(provider.getModelPreferenceKey(), model);
        editor.apply();
    }

    public boolean hasApiKey() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.isEmpty() && !apiKey.contains("your-api-key-here");
    }

    public boolean hasModel() {
        String model = getModel();
        return model != null && !model.isEmpty();
    }

    public void setSummary(long entryId, String summary) {
        editor.putString(KEY_SUMMARY_PREFIX + entryId, summary);
        editor.apply();
    }

    public void setSummary(long entryId, String summary, String language) {
        editor.putString(KEY_SUMMARY_PREFIX + entryId, summary);
        editor.putString(KEY_SUMMARY_LANG_PREFIX + entryId, language);
        editor.apply();
    }

    public String getSummary(long entryId) {
        return sharedPreferences.getString(KEY_SUMMARY_PREFIX + entryId, "");
    }

    public String getSummaryLanguage(long entryId) {
        return sharedPreferences.getString(KEY_SUMMARY_LANG_PREFIX + entryId, null);
    }

    public void setIsSummaryView(long entryId, boolean isSummaryView) {
        sharedPreferences.edit()
                .putBoolean(KEY_WEB_VIEW_MODE + "_" + entryId + "_summary", isSummaryView)
                .apply();
    }

    public boolean getIsSummaryView(long entryId) {
        return sharedPreferences.getBoolean(KEY_WEB_VIEW_MODE + "_" + entryId + "_summary", false);
    }

    public void setTranslatedSummary(long entryId, String summary, String language) {
        editor.putString(KEY_TRANSLATED_SUMMARY_PREFIX + entryId, summary);
        editor.putString(KEY_TRANSLATED_SUMMARY_LANG_PREFIX + entryId, language);
        editor.apply();
    }

    public String getTranslatedSummary(long entryId) {
        return sharedPreferences.getString(KEY_TRANSLATED_SUMMARY_PREFIX + entryId, "");
    }

    public String getTranslatedSummaryLanguage(long entryId) {
        return sharedPreferences.getString(KEY_TRANSLATED_SUMMARY_LANG_PREFIX + entryId, null);
    }

    public boolean getAutoTranslateSummary() {
        return sharedPreferences.getBoolean("autoTranslateSummary", false);
    }

    public void setActiveFab(long entryId, String fab) {
        editor.putString(KEY_ACTIVE_FAB_PREFIX + entryId, fab);
        editor.apply();
    }

    public String getActiveFab(long entryId) {
        return sharedPreferences.getString(KEY_ACTIVE_FAB_PREFIX + entryId, "NONE");
    }

    public boolean hasGroqApiKey() {
        String apiKey = sharedPreferences.getString("groqApiKey", "");
        return apiKey != null && !apiKey.isEmpty();
    }

    public boolean hasGroqModel() {
        String model = sharedPreferences.getString("groqModel", "");
        return model != null && !model.isEmpty();
    }

    public int getSummaryLength() {
        return sharedPreferences.getInt("summaryLength", 150);
    }

    public void initializeDefaultModelOnFirst() {
        ApiProvider provider = getApiProvider();
        if (!sharedPreferences.contains(provider.getModelPreferenceKey())) {
            editor.putString(provider.getModelPreferenceKey(), provider.getDefaultModel());
            editor.apply();
        }
    }

    public boolean hasCompletedOnboarding() {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        editor.putBoolean(KEY_ONBOARDING_COMPLETED, completed);
        editor.apply();
    }

    public boolean hasCompletedApiKeySetup() {
        return sharedPreferences.getBoolean(KEY_API_KEY_SETUP_COMPLETED, false);
    }

    public void setApiKeySetupCompleted(boolean completed) {
        editor.putBoolean(KEY_API_KEY_SETUP_COMPLETED, completed);
        editor.apply();
    }

    public int getTtsSpeechRate() {
        return sharedPreferences.getInt("ttsSpeechRate", 100);
    }

    public void setTtsSpeechRate(int rate) {
        editor.putInt("ttsSpeechRate", rate);
        editor.apply();
    }

    public int getTtsPitch() {
        return sharedPreferences.getInt("ttsPitch", 100);
    }

    public void setTtsPitch(int pitch) {
        editor.putInt("ttsPitch", pitch);
        editor.apply();
    }
}

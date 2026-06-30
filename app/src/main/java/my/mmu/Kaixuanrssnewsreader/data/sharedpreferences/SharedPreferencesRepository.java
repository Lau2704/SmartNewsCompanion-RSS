
package my.mmu.Kaixuanrssnewsreader.data.sharedpreferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import my.mmu.Kaixuanrssnewsreader.model.ApiKeyEntry;
import my.mmu.Kaixuanrssnewsreader.model.ApiProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

public class SharedPreferencesRepository {

    private static final String TAG = "SharedPreferencesRepository";
    private static final String KEY_API_PROVIDER = "apiProvider";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_API_KEY_SETUP_COMPLETED = "api_key_setup_completed";
    private static final String MULTI_KEY_SUFFIX = "_api_keys";
    private static final String ACTIVE_KEY_ID_SUFFIX = "_active_key_id";
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
    private static final String KEY_TRANSLATED_TITLE_PREFIX = "translated_title_";
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

    public int getExtractionRetryDelay() {
        return sharedPreferences.getInt("extractionRetryDelay", 10);
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
        migrateSingleKeyIfNeeded(provider.getKey());
        return getActiveApiKey(provider.getKey());
    }

    public void setApiKey(String apiKey) {
        ApiProvider provider = getApiProvider();
        migrateSingleKeyIfNeeded(provider.getKey());
        List<ApiKeyEntry> keys = getApiKeys(provider.getKey());
        String activeId = getActiveApiKeyId(provider.getKey());
        if (activeId != null && !activeId.isEmpty()) {
            for (int i = 0; i < keys.size(); i++) {
                if (keys.get(i).getId().equals(activeId)) {
                    keys.set(i, new ApiKeyEntry(activeId, keys.get(i).getLabel(), apiKey, keys.get(i).getCreatedAt()));
                    saveApiKeys(provider.getKey(), keys);
                    return;
                }
            }
        }
        if (!apiKey.isEmpty()) {
            addApiKey(provider.getKey(), "Default", apiKey);
        }
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

    public List<ApiKeyEntry> getApiKeys(String providerKey) {
        migrateSingleKeyIfNeeded(providerKey);
        String json = sharedPreferences.getString(providerKey + MULTI_KEY_SUFFIX, null);
        List<ApiKeyEntry> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(ApiKeyEntry.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing API keys for " + providerKey, e);
        }
        return result;
    }

    public void saveApiKeys(String providerKey, List<ApiKeyEntry> keys) {
        JSONArray array = new JSONArray();
        for (ApiKeyEntry entry : keys) {
            try {
                array.put(entry.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "Error serializing API key entry", e);
            }
        }
        editor.putString(providerKey + MULTI_KEY_SUFFIX, array.toString());
        editor.apply();
    }

    public void addApiKey(String providerKey, String label, String apiKey) {
        migrateSingleKeyIfNeeded(providerKey);
        List<ApiKeyEntry> keys = getApiKeys(providerKey);
        ApiKeyEntry newEntry = new ApiKeyEntry(label, apiKey);
        keys.add(newEntry);
        saveApiKeys(providerKey, keys);
        if (keys.size() == 1) {
            setActiveApiKeyId(providerKey, newEntry.getId());
        }
    }

    public void removeApiKey(String providerKey, String keyId) {
        List<ApiKeyEntry> keys = getApiKeys(providerKey);
        String activeId = getActiveApiKeyId(providerKey);
        boolean removingActive = keyId.equals(activeId);
        keys.removeIf(entry -> entry.getId().equals(keyId));
        saveApiKeys(providerKey, keys);
        if (removingActive && !keys.isEmpty()) {
            setActiveApiKeyId(providerKey, keys.get(0).getId());
        } else if (keys.isEmpty()) {
            editor.remove(providerKey + ACTIVE_KEY_ID_SUFFIX);
            editor.apply();
        }
    }

    public void setActiveApiKeyId(String providerKey, String keyId) {
        editor.putString(providerKey + ACTIVE_KEY_ID_SUFFIX, keyId);
        editor.apply();
    }

    public boolean rotateToNextApiKey() {
        ApiProvider provider = getApiProvider();
        return rotateToNextApiKey(provider.getKey());
    }

    public boolean rotateToNextApiKey(String providerKey) {
        List<ApiKeyEntry> keys = getApiKeys(providerKey);
        if (keys.size() <= 1) return false;
        String activeId = getActiveApiKeyId(providerKey);
        int currentIndex = -1;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).getId().equals(activeId)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % keys.size();
        setActiveApiKeyId(providerKey, keys.get(nextIndex).getId());
        Log.d(TAG, "Rotated API key for " + providerKey + " from index " + currentIndex + " to " + nextIndex);
        return true;
    }

    public String getActiveApiKeyId(String providerKey) {
        return sharedPreferences.getString(providerKey + ACTIVE_KEY_ID_SUFFIX, null);
    }

    public String getActiveApiKey(String providerKey) {
        String activeId = getActiveApiKeyId(providerKey);
        if (activeId == null || activeId.isEmpty()) {
            List<ApiKeyEntry> keys = getApiKeys(providerKey);
            if (!keys.isEmpty()) {
                setActiveApiKeyId(providerKey, keys.get(0).getId());
                return keys.get(0).getKey();
            }
            return "";
        }
        List<ApiKeyEntry> keys = getApiKeys(providerKey);
        for (ApiKeyEntry entry : keys) {
            if (entry.getId().equals(activeId)) {
                return entry.getKey();
            }
        }
        if (!keys.isEmpty()) {
            setActiveApiKeyId(providerKey, keys.get(0).getId());
            return keys.get(0).getKey();
        }
        return "";
    }

    private void migrateSingleKeyIfNeeded(String providerKey) {
        String multiKeyJson = sharedPreferences.getString(providerKey + MULTI_KEY_SUFFIX, null);
        if (multiKeyJson != null) return;
        String singleKey = sharedPreferences.getString(providerKey + "ApiKey", null);
        if (singleKey == null || singleKey.isEmpty()) return;
        Log.d(TAG, "Migrating single API key to multi-key format for " + providerKey);
        ApiKeyEntry entry = new ApiKeyEntry("Default", singleKey);
        JSONArray array = new JSONArray();
        try {
            array.put(entry.toJson());
        } catch (JSONException e) {
            Log.e(TAG, "Error migrating API key", e);
            return;
        }
        editor.putString(providerKey + MULTI_KEY_SUFFIX, array.toString());
        editor.putString(providerKey + ACTIVE_KEY_ID_SUFFIX, entry.getId());
        editor.apply();
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

    public void setTranslatedTitle(long entryId, String title) {
        editor.putString(KEY_TRANSLATED_TITLE_PREFIX + entryId, title);
        editor.apply();
    }

    public String getTranslatedTitle(long entryId) {
        return sharedPreferences.getString(KEY_TRANSLATED_TITLE_PREFIX + entryId, "");
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

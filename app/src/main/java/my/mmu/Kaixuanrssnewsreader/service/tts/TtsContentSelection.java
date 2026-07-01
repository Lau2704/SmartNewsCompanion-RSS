package my.mmu.Kaixuanrssnewsreader.service.tts;

import androidx.annotation.Nullable;

import my.mmu.Kaixuanrssnewsreader.data.entry.Entry;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;

public class TtsContentSelection {

    public static final String MODE_ORIGINAL = "ORIGINAL";
    public static final String MODE_TRANSLATED = "TRANSLATED";
    public static final String MODE_SUMMARY = "SUMMARY";
    public static final String MODE_TRANSLATED_SUMMARY = "TRANSLATED_SUMMARY";

    @Nullable
    public final String content;
    @Nullable
    public final String language;
    public final String mode;

    public TtsContentSelection(@Nullable String content, @Nullable String language, String mode) {
        this.content = content;
        this.language = language;
        this.mode = mode;
    }

    public static TtsContentSelection select(
            long entryId,
            @Nullable Entry entry,
            @Nullable String entryTitle,
            @Nullable String feedLanguage,
            @Nullable String defaultTranslationLanguage,
            SharedPreferencesRepository prefs) {

        String savedActiveFab = prefs.getActiveFab(entryId);
        boolean isSummaryView = prefs.getIsSummaryView(entryId);
        boolean isTranslatedView = prefs.getIsTranslatedView(entryId);

        String savedSummary = prefs.getSummary(entryId);
        String savedTranslatedSummary = prefs.getTranslatedSummary(entryId);
        boolean hasSummary = savedSummary != null && !savedSummary.isEmpty();
        boolean hasTransSummary = savedTranslatedSummary != null && !savedTranslatedSummary.isEmpty();

        if (isSummaryView && "TRANSLATE_SUMMARY".equals(savedActiveFab) && hasTransSummary) {
            String cachedTitle = prefs.getTranslatedTitle(entryId);
            String title = (cachedTitle != null && !cachedTitle.isEmpty()) ? cachedTitle : entryTitle;
            String content = (title != null ? title + ". " : "") + savedTranslatedSummary;
            String lang = prefs.getTranslatedSummaryLanguage(entryId);
            if (lang == null || lang.isEmpty()) lang = defaultTranslationLanguage;
            return new TtsContentSelection(content, lang, MODE_TRANSLATED_SUMMARY);
        }

        if (isSummaryView && hasSummary) {
            String content = (entryTitle != null ? entryTitle + ". " : "") + savedSummary;
            String lang = feedLanguage != null ? feedLanguage : "en";
            return new TtsContentSelection(content, lang, MODE_SUMMARY);
        }

        if (isTranslatedView && entry != null
                && entry.getTranslated() != null
                && !entry.getTranslated().trim().isEmpty()) {
            return new TtsContentSelection(entry.getTranslated(), defaultTranslationLanguage, MODE_TRANSLATED);
        }

        String original = entry != null ? entry.getContent() : "";
        String lang = feedLanguage != null ? feedLanguage : "en";
        return new TtsContentSelection(original, lang, MODE_ORIGINAL);
    }
}

package my.mmu.Kaixuanrssnewsreader.ui.feedsetting;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;

import my.mmu.Kaixuanrssnewsreader.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LanguageSelectionDialog extends AppCompatDialogFragment {

    public static final String TAG = "LanguageSelectionDialog";
    public static final String languageIdentifierTAG = "Use Language Identifier";

    private RadioGroup radioGroup;
    private FeedSettingDialogListener listener;
    private Map<String, Integer> languagesMap;
    private Set<String> installedLanguageCodes;
    private TextToSpeech tts;

    public LanguageSelectionDialog(FeedSettingDialogListener listener, Context context) {
        this.listener = listener;

        tts = new TextToSpeech(context, status -> {
            Log.d(TAG, "TTS init status: " + status);
            if (listener != null) {
                listener.showDialog();
            }
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_languageselection, null);
        radioGroup = view.findViewById(R.id.languageRadioGroup);

        buildInstalledLanguageSet();

        String[] names = getResources().getStringArray(R.array.defaultTranslationLanguage);
        String[] codes = getResources().getStringArray(R.array.defaultTranslationLanguage_values);
        String notInstalledSuffix = " (" + getString(R.string.language_current_tts_not_installed) + ")";

        String savedLanguage = null;
        if (getArguments() != null) {
            savedLanguage = getArguments().getString("language");
        }

        languagesMap = new HashMap<>();
        int idCounter = 0;
        int checkId = 0;

        idCounter++;
        int identifierId = idCounter;
        languagesMap.put(languageIdentifierTAG, identifierId);
        RadioButton identifierRadioButton = new RadioButton(requireContext());
        identifierRadioButton.setText(R.string.languageIdentifier);
        identifierRadioButton.setId(identifierId);
        identifierRadioButton.setLayoutParams(new RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.MATCH_PARENT));
        radioGroup.addView(identifierRadioButton);

        for (int i = 0; i < codes.length && i < names.length; i++) {
            idCounter++;
            String code = codes[i];
            String name = names[i];
            languagesMap.put(code, idCounter);

            boolean installed = isInstalled(code);
            boolean isSaved = code.equals(savedLanguage);

            RadioButton radioButton = new RadioButton(requireContext());
            radioButton.setText(name + (installed ? "" : notInstalledSuffix));
            radioButton.setId(idCounter);
            radioButton.setEnabled(installed || isSaved);
            radioButton.setLayoutParams(new RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.MATCH_PARENT));
            radioGroup.addView(radioButton);

            if (isSaved) {
                checkId = idCounter;
            }
        }

        if (savedLanguage == null) {
            checkId = identifierId;
        }
        if (checkId != 0) {
            radioGroup.check(checkId);
        }

        if (tts != null) {
            tts.shutdown();
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setView(view)
                .setIcon(R.drawable.ic_setting)
                .setTitle(R.string.select_language)
                .setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setPositiveButton(R.string.select, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        int radioId = radioGroup.getCheckedRadioButtonId();
                        String language = getKey(radioId);
                        listener.modifyLanguage((String) language);
                    }
                });

        return builder.create();
    }

    private void buildInstalledLanguageSet() {
        installedLanguageCodes = new HashSet<>();
        if (tts == null) {
            return;
        }
        try {
            Set<Locale> available = tts.getAvailableLanguages();
            if (available != null) {
                for (Locale locale : available) {
                    String lang = locale.getLanguage();
                    if (lang != null && !lang.isEmpty()) {
                        installedLanguageCodes.add(lang);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting available languages", e);
        }
    }

    private boolean isInstalled(String code) {
        return installedLanguageCodes != null && installedLanguageCodes.contains(code);
    }

    private String getKey(int id) {
        for (Map.Entry<String, Integer> entry : languagesMap.entrySet()) {
            if (Objects.equals(id, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}

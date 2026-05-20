package my.mmu.Kaixuanrssnewsreader.ui.setting;

import static android.app.Activity.RESULT_OK;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.PreferenceFragmentCompat;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.model.ApiProvider;
import my.mmu.Kaixuanrssnewsreader.service.rss.RssWorkManager;
import my.mmu.Kaixuanrssnewsreader.service.util.LocalLlmEngine;
import my.mmu.Kaixuanrssnewsreader.service.util.LocalModelDownloader;
import my.mmu.Kaixuanrssnewsreader.service.util.ModelDownloadService;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsPlayer;
import my.mmu.Kaixuanrssnewsreader.ui.main.MainActivity;
import my.mmu.Kaixuanrssnewsreader.ui.setupwebview.SetupWebViewActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String GOOGLE_TTS_PACKAGE = "com.google.android.tts";

    @Inject
    RssWorkManager rssWorkManager;

    @Inject
    TtsPlayer ttsPlayer;

    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;

    @Inject
    LocalLlmEngine localLlmEngine;

    private ListPreference backgroundMusicFilePreference;
    private EditTextPreference apiKeyPreference;
    private EditTextPreference modelPreference;
    private Preference localModelDownloadPreference;
    private Preference localModelDeletePreference;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private boolean isAdditionalImport;
    private AlertDialog downloadDialog;
    private BroadcastReceiver downloadReceiver;
    private Handler progressPollHandler;
    private ProgressBar dialogProgressBar;
    private TextView dialogProgressText;
    private final CharSequence[] defaultMusicEntries = {"Default", "Import music file (ogg format is preferred)"};
    private final CharSequence[] defaultMusicValues = {"default", "userFile"};
    private final CharSequence[] extendedMusicEntries  = {"Default", "Imported music file", "Import another music file (ogg format is preferred)"};
    private final CharSequence[] extendedMusicValues  = {"default", "userFile", "addUserFile"};

    private SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case "jobPeriodic":
                    rssWorkManager.enqueueRssWorker();
                    break;
                case "night":
                    boolean night = sharedPreferencesRepository.getNight();
                    if (night) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    }
                    ((MainActivity) getActivity()).updateThemeSwitch();
                    break;
                case "backgroundMusic":
                    if (ttsPlayer.isPlayingMediaPlayer()) {
                        boolean backgroundMusic = sharedPreferencesRepository.getBackgroundMusic();
                        if (backgroundMusic) {
                            ttsPlayer.setupMediaPlayer(false);
                        } else {
                            ttsPlayer.stopMediaPlayer();
                        }
                    }
                    break;
                case "backgroundMusicFile":
                    String musicFile = sharedPreferencesRepository.getBackgroundMusicFile();
                    if (!musicFile.equals("default")) {
                        if (!musicFile.equals("userFile")) {
                            isAdditionalImport = true;
                            sharedPreferencesRepository.setBackgroundMusicFile("userFile");
                            backgroundMusicFilePreference.setValue("userFile");
                        } else {
                            backgroundMusicFilePreference.setEntries(extendedMusicEntries);
                            backgroundMusicFilePreference.setEntryValues(extendedMusicValues);
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                            intent.setType("audio/*");
                            saveMusicFileLauncher.launch(intent);
                        }
                    } else {
                        backgroundMusicFilePreference.setEntries(defaultMusicEntries);
                        backgroundMusicFilePreference.setEntryValues(defaultMusicValues);
                        if (ttsPlayer.isPlayingMediaPlayer()) {
                            ttsPlayer.setupMediaPlayer(true);
                        }
                    }
                    break;
                case "backgroundMusicVolume":
                    ttsPlayer.changeMediaPlayerVolume();
                    break;
                case "apiProvider":
                    updatePreferenceKeysForProvider(sharedPreferencesRepository.getApiProvider());
                    break;
                case "ttsSpeechRate":
                    ttsPlayer.applyTtsSettings();
                    break;
                case "ttsPitch":
                    ttsPlayer.applyTtsSettings();
                    break;
                case "appLanguage":
                    String lang = sharedPreferences.getString("appLanguage", "system");
                    if ("system".equals(lang)) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                    } else {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));
                    }
                    break;
                case "autoTranslate":
                    if (sharedPreferences.getBoolean("autoTranslate", false)) {
                        disableOtherAutoSettings("autoTranslate");
                    }
                    break;
                case "displaySummary":
                    if (sharedPreferences.getBoolean("displaySummary", false)) {
                        disableOtherAutoSettings("displaySummary");
                    }
                    break;
                case "autoTranslateSummary":
                    if (sharedPreferences.getBoolean("autoTranslateSummary", false)) {
                        disableOtherAutoSettings("autoTranslateSummary");
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        backgroundMusicFilePreference = findPreference("backgroundMusicFile");

        apiKeyPreference = findPreference("groqApiKey");
        modelPreference = findPreference("groqModel");
        updatePreferenceKeysForProvider(sharedPreferencesRepository.getApiProvider());

        if (!sharedPreferencesRepository.getBackgroundMusicFile().equals("default")) {
            backgroundMusicFilePreference.setEntries(extendedMusicEntries);
            backgroundMusicFilePreference.setEntryValues(extendedMusicValues);
        } else {
            backgroundMusicFilePreference.setEntries(defaultMusicEntries);
            backgroundMusicFilePreference.setEntryValues(defaultMusicValues);
        }

        Preference apiKeySetupPreference = findPreference("api_key_setup");
        if (apiKeySetupPreference != null) {
            apiKeySetupPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    ApiProvider provider = sharedPreferencesRepository.getApiProvider();
                    Intent intent = new Intent(getActivity(), SetupWebViewActivity.class);
                    intent.putExtra(SetupWebViewActivity.EXTRA_TITLE, getString(R.string.api_key_setup_title));
                    intent.putExtra(SetupWebViewActivity.EXTRA_INSTRUCTION, getString(provider.getInstructionRes()));
                    intent.putExtra(SetupWebViewActivity.EXTRA_PROVIDER, provider.getKey());
                    startActivity(intent);
                    return true;
                }
            });
        }

        localModelDownloadPreference = findPreference("local_model_download");
        localModelDeletePreference = findPreference("local_model_delete");

        if (localModelDownloadPreference != null) {
            updateLocalModelPreference();
            localModelDownloadPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    if (LocalModelDownloader.isModelDownloaded(requireContext())) {
                        File modelFile = LocalModelDownloader.getModelFile(requireContext());
                        String size = LocalModelDownloader.formatFileSize(LocalModelDownloader.getDownloadedSize(requireContext()));
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.local_model_file_location)
                                .setMessage(modelFile.getAbsolutePath() + "\n\n" + size)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    } else {
                        downloadLocalModel();
                    }
                    return true;
                }
            });
        }

        if (localModelDeletePreference != null) {
            localModelDeletePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(R.string.local_model_delete)
                            .setMessage(R.string.local_model_delete_confirm)
                            .setPositiveButton(R.string.delete, (dialog, which) -> {
                                localLlmEngine.unloadModel();
                                LocalModelDownloader.deleteModel(requireContext());
                                updateLocalModelPreference();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                }
            });
        }

        Preference installGoogleTtsPreference = findPreference("key_install_google_tts");
        if (installGoogleTtsPreference != null) {
            updateGoogleTtsInstallPreference(installGoogleTtsPreference);
            installGoogleTtsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    if (isGoogleTtsInstalled()) {
                        startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                    } else {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GOOGLE_TTS_PACKAGE)));
                        } catch (android.content.ActivityNotFoundException e) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE_TTS_PACKAGE)));
                        }
                    }
                    return true;
                }
            });
        }

        Preference voiceDataPreference = findPreference("key_google_tts_voice_data");
        if (voiceDataPreference != null) {
            voiceDataPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    try {
                        Intent directIntent = new Intent();
                        directIntent.setClassName(GOOGLE_TTS_PACKAGE, "com.google.android.tts.settings.VoiceDataSettingsActivity");
                        startActivity(directIntent);
                    } catch (Exception e) {
                        try {
                            Intent installIntent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                            installIntent.setPackage(GOOGLE_TTS_PACKAGE);
                            startActivity(installIntent);
                        } catch (Exception e2) {
                            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                        }
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).registerOnSharedPreferenceChangeListener(listener);
        Preference installGoogleTtsPreference = findPreference("key_install_google_tts");
        if (installGoogleTtsPreference != null) {
            updateGoogleTtsInstallPreference(installGoogleTtsPreference);
        }

        updatePreferenceKeysForProvider(sharedPreferencesRepository.getApiProvider());
    }

    @Override
    public void onPause() {
        super.onPause();
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(listener);
    }

    // Define the ActivityResultLauncher for saving the music file
    private final ActivityResultLauncher<Intent> saveMusicFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            // Handle the selected audio file
                            Uri fileUri = data.getData();
                            handleSelectedFile(fileUri);
                        }
                    } else {
                        // File selection canceled or failed
                        if (isAdditionalImport) {
                            isAdditionalImport = false;
                        } else {
                            sharedPreferencesRepository.setBackgroundMusicFile("default");
                            backgroundMusicFilePreference.setValue("default");
                        }
                        Toast.makeText(requireContext(), "File selection canceled or failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private void updatePreferenceKeysForProvider(ApiProvider provider) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null || apiKeyPreference == null || modelPreference == null) {
            return;
        }

        boolean isLocal = provider.isLocal();

        Preference apiKeySetupPreference = findPreference("api_key_setup");
        if (apiKeySetupPreference != null) {
            apiKeySetupPreference.setVisible(!isLocal);
        }
        apiKeyPreference.setVisible(!isLocal);
        modelPreference.setVisible(!isLocal);

        if (localModelDownloadPreference != null) {
            localModelDownloadPreference.setVisible(isLocal);
        }
        if (localModelDeletePreference != null) {
            localModelDeletePreference.setVisible(isLocal && LocalModelDownloader.isModelDownloaded(requireContext()));
        }

        if (isLocal) {
            updateLocalModelPreference();
            return;
        }

        String apiKeyPrefKey = provider.getApiKeyPreferenceKey();
        String modelPrefKey = provider.getModelPreferenceKey();

        apiKeyPreference.setKey(apiKeyPrefKey);
        modelPreference.setKey(modelPrefKey);

        String currentApiKey = prefs.getString(apiKeyPrefKey, "");
        String currentModel = prefs.getString(modelPrefKey, "");

        if (currentModel == null || currentModel.isEmpty()) {
            currentModel = provider.getDefaultModel();
            prefs.edit().putString(modelPrefKey, currentModel).apply();
        }

        apiKeyPreference.setText(currentApiKey != null ? currentApiKey : "");
        modelPreference.setText(currentModel);
    }

    private void updateLocalModelPreference() {
        if (localModelDownloadPreference == null) return;

        boolean downloaded = LocalModelDownloader.isModelDownloaded(requireContext());
        if (downloaded) {
            String size = LocalModelDownloader.formatFileSize(LocalModelDownloader.getDownloadedSize(requireContext()));
            localModelDownloadPreference.setTitle(getString(R.string.local_model_downloaded, size));
            localModelDownloadPreference.setSummary(getString(R.string.local_model_name));
        } else {
            localModelDownloadPreference.setTitle(getString(R.string.local_model_download) + " - " + getString(R.string.local_model_name));
            localModelDownloadPreference.setSummary(R.string.local_model_not_downloaded);
        }

        if (localModelDeletePreference != null) {
            localModelDeletePreference.setVisible(downloaded);
        }
    }

    private void downloadLocalModel() {
        boolean reconnecting = LocalModelDownloader.isDownloading();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_model_download, null);
        dialogProgressBar = dialogView.findViewById(R.id.downloadProgressBar);
        dialogProgressText = dialogView.findViewById(R.id.downloadProgressText);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelDownload);
        Button btnPauseResume = dialogView.findViewById(R.id.btnPauseResumeDownload);
        Button btnBackground = dialogView.findViewById(R.id.btnBackgroundDownload);

        if (reconnecting) {
            int progress = LocalModelDownloader.getCurrentProgress();
            dialogProgressBar.setProgress(progress);
            dialogProgressText.setText(getString(R.string.local_model_download_progress, getString(R.string.local_model_name), progress));
            if (LocalModelDownloader.isPaused()) {
                btnPauseResume.setText(R.string.local_model_download_continue);
            } else {
                btnPauseResume.setText(R.string.local_model_download_pause);
            }
        }

        downloadDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.local_model_downloading)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        downloadDialog.setOnDismissListener(dialog -> stopProgressPolling());

        btnCancel.setOnClickListener(v -> {
            LocalModelDownloader.cancelDownload(requireContext());
            requireContext().stopService(new Intent(requireContext(), ModelDownloadService.class));
            downloadDialog.dismiss();
        });

        btnPauseResume.setOnClickListener(v -> {
            if (LocalModelDownloader.isDownloading()) {
                String currentText = btnPauseResume.getText().toString();
                if (currentText.equals(getString(R.string.local_model_download_pause))) {
                    LocalModelDownloader.pauseDownload();
                    btnPauseResume.setText(R.string.local_model_download_continue);
                } else {
                    LocalModelDownloader.resumeDownload();
                    btnPauseResume.setText(R.string.local_model_download_pause);
                }
            }
        });

        btnBackground.setOnClickListener(v -> downloadDialog.dismiss());

        registerDownloadReceiver();

        if (!reconnecting) {
            Intent serviceIntent = new Intent(requireContext(), ModelDownloadService.class);
            ContextCompat.startForegroundService(requireContext(), serviceIntent);
        }

        downloadDialog.show();
        startProgressPolling();
    }

    private void startProgressPolling() {
        progressPollHandler = new Handler(Looper.getMainLooper());
        progressPollHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (downloadDialog != null && downloadDialog.isShowing()) {
                    int progress = LocalModelDownloader.getCurrentProgress();
                    if (dialogProgressBar != null) {
                        dialogProgressBar.setProgress(progress);
                    }
                    if (dialogProgressText != null) {
                        dialogProgressText.setText(getString(R.string.local_model_download_progress, getString(R.string.local_model_name), progress));
                    }
                    if (LocalModelDownloader.isDownloading()) {
                        progressPollHandler.postDelayed(this, 500);
                    }
                }
            }
        }, 500);
    }

    private void stopProgressPolling() {
        if (progressPollHandler != null) {
            progressPollHandler.removeCallbacksAndMessages(null);
            progressPollHandler = null;
        }
        dialogProgressBar = null;
        dialogProgressText = null;
    }

    private void registerDownloadReceiver() {
        if (downloadReceiver != null) return;
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ModelDownloadService.ACTION_COMPLETE.equals(intent.getAction())) {
                    if (downloadDialog != null && downloadDialog.isShowing()) {
                        downloadDialog.dismiss();
                    }
                    stopProgressPolling();
                    updateLocalModelPreference();
                    Toast.makeText(requireContext(), getString(R.string.local_model_downloaded, LocalModelDownloader.formatFileSize(LocalModelDownloader.getDownloadedSize(requireContext()))), Toast.LENGTH_LONG).show();
                    unregisterDownloadReceiver();
                } else if (ModelDownloadService.ACTION_FAILED.equals(intent.getAction())) {
                    if (downloadDialog != null && downloadDialog.isShowing()) {
                        downloadDialog.dismiss();
                    }
                    stopProgressPolling();
                    String error = intent.getStringExtra("error");
                    if (error != null && !"Download cancelled".equals(error)) {
                        Toast.makeText(requireContext(), "Download failed: " + error, Toast.LENGTH_LONG).show();
                    }
                    unregisterDownloadReceiver();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ModelDownloadService.ACTION_COMPLETE);
        filter.addAction(ModelDownloadService.ACTION_FAILED);
        requireActivity().registerReceiver(downloadReceiver, filter);
    }

    private void unregisterDownloadReceiver() {
        if (downloadReceiver != null) {
            try {
                requireActivity().unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {}
            downloadReceiver = null;
        }
    }

    private void handleSelectedFile(Uri fileUri) {
        File internalStorageDir = getActivity().getFilesDir();

        // Create a File object for the destination file in internal storage
        File destinationFile = new File(internalStorageDir, "user_file.mp3");

        // Copy the user-inserted file to the destination in internal storage
        try {
            InputStream inputStream = getActivity().getContentResolver().openInputStream(fileUri);
            OutputStream outputStream = new FileOutputStream(destinationFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();
            Toast.makeText(requireContext(), "File imported successfully", Toast.LENGTH_SHORT).show();
            if (ttsPlayer.isPlayingMediaPlayer()) {
                ttsPlayer.setupMediaPlayer(true);
            }
        } catch (IOException e) {
            sharedPreferencesRepository.setBackgroundMusicFile("default");
            e.printStackTrace();
        }
    }

    private boolean isGoogleTtsInstalled() {
        try {
            requireContext().getPackageManager().getPackageInfo(GOOGLE_TTS_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void updateGoogleTtsInstallPreference(Preference preference) {
        if (isGoogleTtsInstalled()) {
            preference.setTitle(getString(R.string.install_google_tts_title_installed));
            preference.setSummary(getString(R.string.install_google_tts_summary_installed));
        } else {
            preference.setTitle(getString(R.string.install_google_tts_title));
            preference.setSummary(getString(R.string.install_google_tts_summary_not_installed));
        }
    }

    private void disableOtherAutoSettings(String enabledKey) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) return;
        SharedPreferences.Editor ed = prefs.edit();
        if (!"autoTranslate".equals(enabledKey)) {
            ed.putBoolean("autoTranslate", false);
            SwitchPreferenceCompat pref = findPreference("autoTranslate");
            if (pref != null) pref.setChecked(false);
        }
        if (!"displaySummary".equals(enabledKey)) {
            ed.putBoolean("displaySummary", false);
            SwitchPreferenceCompat pref = findPreference("displaySummary");
            if (pref != null) pref.setChecked(false);
        }
        if (!"autoTranslateSummary".equals(enabledKey)) {
            ed.putBoolean("autoTranslateSummary", false);
            SwitchPreferenceCompat pref = findPreference("autoTranslateSummary");
            if (pref != null) pref.setChecked(false);
        }
        ed.apply();
    }

    @Override
    public void onDestroyView() {
        stopProgressPolling();
        unregisterDownloadReceiver();
        compositeDisposable.dispose();
        super.onDestroyView();
    }
}
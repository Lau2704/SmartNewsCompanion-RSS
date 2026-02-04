package my.mmu.Kaixuanrssnewsreader.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.databinding.ActivityOnboardingBinding;
import my.mmu.Kaixuanrssnewsreader.ui.main.MainActivity;
import my.mmu.Kaixuanrssnewsreader.ui.setupwebview.SetupWebViewActivity;

import com.google.android.material.button.MaterialButton;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;

    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPreferencesRepository.initializeDefaultModelOnFirst();

        setupButtons();
    }

    private void setupButtons() {
        MaterialButton setupButton = binding.onboardingSetupButton;
        MaterialButton skipButton = binding.onboardingSkipButton;

        setupButton.setOnClickListener(v -> startApiKeySetup());

        skipButton.setOnClickListener(v -> {
            sharedPreferencesRepository.setOnboardingCompleted(true);
            navigateToMain();
        });
    }

    private void startApiKeySetup() {
        Intent intent = new Intent(this, SetupWebViewActivity.class);
        intent.putExtra(SetupWebViewActivity.EXTRA_URL, "https://openrouter.ai/settings/keys");
        intent.putExtra(SetupWebViewActivity.EXTRA_TITLE, "API Key Setup");
        intent.putExtra(SetupWebViewActivity.EXTRA_INSTRUCTION, getString(my.mmu.Kaixuanrssnewsreader.R.string.api_key_instruction));
        intent.putExtra(SetupWebViewActivity.EXTRA_STEP, "api_key");
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startPrivacySetup();
        } else if (requestCode == 101 && resultCode == RESULT_OK) {
            sharedPreferencesRepository.setOnboardingCompleted(true);
            navigateToMain();
        }
    }

    private void startPrivacySetup() {
        Intent intent = new Intent(this, SetupWebViewActivity.class);
        intent.putExtra(SetupWebViewActivity.EXTRA_URL, "https://openrouter.ai/settings/privacy");
        intent.putExtra(SetupWebViewActivity.EXTRA_TITLE, "Privacy Settings");
        intent.putExtra(SetupWebViewActivity.EXTRA_INSTRUCTION, getString(my.mmu.Kaixuanrssnewsreader.R.string.privacy_instruction));
        intent.putExtra(SetupWebViewActivity.EXTRA_STEP, "privacy");
        startActivityForResult(intent, 101);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

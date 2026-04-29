package my.mmu.Kaixuanrssnewsreader.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.model.ApiProvider;
import my.mmu.Kaixuanrssnewsreader.databinding.ActivityOnboardingBinding;
import my.mmu.Kaixuanrssnewsreader.ui.main.MainActivity;
import my.mmu.Kaixuanrssnewsreader.ui.setupwebview.SetupWebViewActivity;

import com.google.android.material.button.MaterialButton;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class OnboardingActivity extends AppCompatActivity {

    private static final int REQUEST_SETUP = 100;

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
        ApiProvider provider = sharedPreferencesRepository.getApiProvider();
        Intent intent = new Intent(this, SetupWebViewActivity.class);
        intent.putExtra(SetupWebViewActivity.EXTRA_TITLE, getString(R.string.api_key_setup_title));
        intent.putExtra(SetupWebViewActivity.EXTRA_INSTRUCTION, getString(provider.getInstructionRes()));
        intent.putExtra(SetupWebViewActivity.EXTRA_PROVIDER, provider.getKey());
        intent.putExtra(SetupWebViewActivity.EXTRA_FROM_ONBOARDING, true);
        startActivityForResult(intent, REQUEST_SETUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETUP && resultCode == RESULT_OK) {
            navigateToMain();
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

package my.mmu.Kaixuanrssnewsreader.ui.setupwebview;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.databinding.ActivitySetupWebviewBinding;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SetupWebViewActivity extends AppCompatActivity {

    private static final String TAG = "SetupWebViewActivity";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_INSTRUCTION = "instruction";
    public static final String EXTRA_FROM_ONBOARDING = "from_onboarding";

    private ActivitySetupWebviewBinding binding;
    private boolean isInstructionExpanded = true;
    private boolean isFromOnboarding = false;

    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String instruction = getIntent().getStringExtra(EXTRA_INSTRUCTION);
        isFromOnboarding = getIntent().getBooleanExtra(EXTRA_FROM_ONBOARDING, false);

        binding = ActivitySetupWebviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar(title);
        setupInstructionPanel(instruction);
        setupOpenConsoleButton();
        setupApiKeyInput();
        setupCompleteButton();
        setupSkipButton();

        String existingKey = sharedPreferencesRepository.getGroqApiKey();
        if (existingKey != null && !existingKey.isEmpty()) {
            binding.setupApiKeyInput.setText(existingKey);
        }
    }

    private void setupToolbar(String title) {
        MaterialToolbar toolbar = binding.setupToolbar;
        if (title != null && !title.isEmpty()) {
            toolbar.setTitle(title);
        }
        toolbar.setNavigationOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void setupInstructionPanel(String instruction) {
        if (instruction != null && !instruction.isEmpty()) {
            binding.setupInstructionText.setText(instruction);
        } else {
            binding.setupInstructionText.setText(R.string.groq_api_key_instruction);
        }

        binding.setupInstructionExpand.setOnClickListener(v -> {
            isInstructionExpanded = !isInstructionExpanded;
            binding.setupInstructionContent.setVisibility(isInstructionExpanded ? View.VISIBLE : View.GONE);
            binding.setupInstructionExpand.setImageResource(
                isInstructionExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more
            );
        });
    }

    private void setupOpenConsoleButton() {
        MaterialButton openConsoleButton = binding.setupOpenConsoleButton;
        openConsoleButton.setOnClickListener(v -> {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            customTabsIntent.launchUrl(this, Uri.parse("https://console.groq.com/keys"));
        });
    }

    private void setupApiKeyInput() {
        TextInputEditText apiKeyInput = binding.setupApiKeyInput;
        apiKeyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String apiKey = s != null ? s.toString().trim() : "";
                binding.setupCompleteButton.setEnabled(!apiKey.isEmpty());
            }
        });
    }

    private void setupCompleteButton() {
        MaterialButton completeButton = binding.setupCompleteButton;
        completeButton.setOnClickListener(v -> {
            String apiKey = binding.setupApiKeyInput.getText() != null
                    ? binding.setupApiKeyInput.getText().toString().trim() : "";

            if (TextUtils.isEmpty(apiKey)) {
                Toast.makeText(this, getString(R.string.enter_api_key_title), Toast.LENGTH_SHORT).show();
                return;
            }

            sharedPreferencesRepository.setGroqApiKey(apiKey);
            sharedPreferencesRepository.setApiKeySetupCompleted(true);

            if (isFromOnboarding) {
                sharedPreferencesRepository.setOnboardingCompleted(true);
            }

            setResult(RESULT_OK);
            finish();
        });
    }

    private void setupSkipButton() {
        MaterialButton skipButton = binding.setupSkipButton;
        if (isFromOnboarding) {
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(v -> {
                sharedPreferencesRepository.setOnboardingCompleted(true);
                setResult(RESULT_OK);
                finish();
            });
        } else {
            skipButton.setVisibility(View.GONE);
        }
    }
}

package my.mmu.Kaixuanrssnewsreader.ui.setting;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.model.ApiKeyEntry;
import my.mmu.Kaixuanrssnewsreader.model.ApiProvider;

@AndroidEntryPoint
public class ApiKeyManagerActivity extends AppCompatActivity {

    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;

    private Spinner providerSpinner;
    private RecyclerView recyclerView;
    private TextView emptyStateText;
    private TextView instructionText;
    private LinearLayout instructionContent;
    private ImageView instructionExpandIcon;
    private MaterialButton openConsoleButton;
    private MaterialButton addKeyButton;
    private MaterialButton completeSetupButton;
    private TextInputEditText apiKeyInput;
    private ApiKeyAdapter adapter;
    private ApiProvider[] providers;
    private boolean isInstructionExpanded = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_key_manager);

        MaterialToolbar toolbar = findViewById(R.id.apiKeyManagerToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        List<ApiProvider> providerList = new ArrayList<>();
        List<String> providerNames = new ArrayList<>();
        for (ApiProvider provider : ApiProvider.values()) {
            if (!provider.isLocal()) {
                providerList.add(provider);
                providerNames.add(getString(provider.getDisplayNameRes()));
            }
        }
        providers = providerList.toArray(new ApiProvider[0]);

        providerSpinner = findViewById(R.id.providerSpinner);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, providerNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(spinnerAdapter);

        ApiProvider currentProvider = sharedPreferencesRepository.getApiProvider();
        for (int i = 0; i < providers.length; i++) {
            if (providers[i] == currentProvider) {
                providerSpinner.setSelection(i);
                break;
            }
        }

        instructionText = findViewById(R.id.instructionText);
        instructionContent = findViewById(R.id.instructionContent);
        instructionExpandIcon = findViewById(R.id.instructionExpandIcon);
        openConsoleButton = findViewById(R.id.openConsoleButton);
        apiKeyInput = findViewById(R.id.apiKeyInput);
        addKeyButton = findViewById(R.id.addKeyButton);
        completeSetupButton = findViewById(R.id.completeSetupButton);
        recyclerView = findViewById(R.id.apiKeysRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);

        setupInstructionCard();
        setupOpenConsoleButton();
        setupApiKeyInput();
        setupAddKeyButton();
        setupCompleteButton();
        setupRecyclerView();

        providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateInstructionText();
                refreshKeys();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        updateInstructionText();
        refreshKeys();
    }

    private void setupInstructionCard() {
        LinearLayout instructionHeader = findViewById(R.id.instructionHeader);
        instructionHeader.setOnClickListener(v -> {
            isInstructionExpanded = !isInstructionExpanded;
            instructionContent.setVisibility(isInstructionExpanded ? View.VISIBLE : View.GONE);
            instructionExpandIcon.setImageResource(
                    isInstructionExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more
            );
        });
    }

    private void updateInstructionText() {
        int pos = providerSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= providers.length) return;
        ApiProvider provider = providers[pos];
        instructionText.setText(provider.getInstructionRes());
    }

    private void setupOpenConsoleButton() {
        openConsoleButton.setOnClickListener(v -> {
            int pos = providerSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= providers.length) return;
            ApiProvider provider = providers[pos];
            String url = provider.getConsoleUrl();
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            customTabsIntent.launchUrl(this, Uri.parse(url));
        });
    }

    private void setupApiKeyInput() {
        apiKeyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String apiKey = s != null ? s.toString().trim() : "";
                addKeyButton.setEnabled(!apiKey.isEmpty());
            }
        });
    }

    private void setupAddKeyButton() {
        addKeyButton.setOnClickListener(v -> {
            String key = apiKeyInput.getText() != null ? apiKeyInput.getText().toString().trim() : "";
            if (key.isEmpty()) return;

            int pos = providerSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= providers.length) return;
            ApiProvider provider = providers[pos];

            String label = "Key " + (sharedPreferencesRepository.getApiKeys(provider.getKey()).size() + 1);
            sharedPreferencesRepository.addApiKey(provider.getKey(), label, key);

            apiKeyInput.setText("");
            refreshKeys();
        });
    }

    private void setupCompleteButton() {
        completeSetupButton.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new ApiKeyAdapter(new ApiKeyAdapter.OnKeyActionListener() {
            @Override
            public void onKeySelected(ApiKeyEntry entry) {
                int pos = providerSpinner.getSelectedItemPosition();
                if (pos < 0 || pos >= providers.length) return;
                ApiProvider selected = providers[pos];
                sharedPreferencesRepository.setActiveApiKeyId(selected.getKey(), entry.getId());
                refreshKeys();
            }

            @Override
            public void onKeyDeleted(ApiKeyEntry entry) {
                new AlertDialog.Builder(ApiKeyManagerActivity.this)
                        .setTitle(R.string.api_key_delete_confirm_title)
                        .setMessage(getString(R.string.api_key_delete_confirm_message, entry.getLabel()))
                        .setPositiveButton(R.string.api_key_delete, (dialog, which) -> {
                            int pos = providerSpinner.getSelectedItemPosition();
                            if (pos < 0 || pos >= providers.length) return;
                            ApiProvider selected = providers[pos];
                            sharedPreferencesRepository.removeApiKey(selected.getKey(), entry.getId());
                            refreshKeys();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void refreshKeys() {
        int pos = providerSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= providers.length) return;
        ApiProvider provider = providers[pos];
        List<ApiKeyEntry> keys = sharedPreferencesRepository.getApiKeys(provider.getKey());
        String activeId = sharedPreferencesRepository.getActiveApiKeyId(provider.getKey());

        adapter.setKeys(keys, activeId != null ? activeId : "");

        TextView storedKeysHeader = findViewById(R.id.storedKeysHeader);
        if (keys.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            storedKeysHeader.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            storedKeysHeader.setVisibility(View.VISIBLE);
        }
    }
}

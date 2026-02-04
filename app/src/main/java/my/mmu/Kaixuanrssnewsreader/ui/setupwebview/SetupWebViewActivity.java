package my.mmu.Kaixuanrssnewsreader.ui.setupwebview;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.databinding.ActivitySetupWebviewBinding;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SetupWebViewActivity extends AppCompatActivity {

    private static final String TAG = "SetupWebViewActivity";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_INSTRUCTION = "instruction";
    public static final String EXTRA_STEP = "step";

    private ActivitySetupWebviewBinding binding;
    private WebView webView;
    private String url;
    private String title;
    private String instruction;
    private String step;
    private boolean isInstructionExpanded = true;

    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        url = getIntent().getStringExtra(EXTRA_URL);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        instruction = getIntent().getStringExtra(EXTRA_INSTRUCTION);
        step = getIntent().getStringExtra(EXTRA_STEP);

        if (url == null || url.isEmpty()) {
            Log.e(TAG, "URL is null or empty");
            finish();
            return;
        }

        binding = ActivitySetupWebviewBinding.inflate(getLayoutInflater());

        setupToolbar();
        setupInstructionPanel();
        setupWebView();
        setupButtons();

        binding.setupInstructionText.setText(instruction);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setResult(RESULT_OK);
                finish();
            }
        });

        webView.loadUrl(url);

        setContentView(binding.getRoot());
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = binding.setupToolbar;
        if (title != null && !title.isEmpty()) {
            toolbar.setTitle(title);
        }
        toolbar.setNavigationOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
    }

    private void setupInstructionPanel() {
        binding.setupInstructionExpand.setOnClickListener(v -> {
            isInstructionExpanded = !isInstructionExpanded;
            binding.setupInstructionContent.setVisibility(isInstructionExpanded ? View.VISIBLE : View.GONE);
            binding.setupInstructionExpand.setImageResource(
                isInstructionExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more
            );
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = binding.setupWebView;
        webView.setWebViewClient(new SetupWebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);

        int textZoom = sharedPreferencesRepository.getTextZoom();
        if (textZoom != 0) {
            webView.getSettings().setTextZoom(textZoom);
        }

        if (sharedPreferencesRepository.getNight()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                webView.getSettings().setForceDark(WebSettings.FORCE_DARK_ON);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                webView.getSettings().setForceDark(WebSettings.FORCE_DARK_OFF);
            }
        }
    }

    private void setupButtons() {
        MaterialButton doneButton = binding.setupDoneButton;
        doneButton.setOnClickListener(v -> markSetupComplete());
    }

    private void markSetupComplete() {
        if ("api_key".equals(step)) {
            sharedPreferencesRepository.setApiKeySetupCompleted(true);
        } else if ("privacy".equals(step)) {
            sharedPreferencesRepository.setPrivacySetupCompleted(true);
        }
        setResult(RESULT_OK);
        finish();
    }

    private class SetupWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            binding.setupLoading.setVisibility(View.VISIBLE);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            binding.setupLoading.setVisibility(View.INVISIBLE);
        }
    }
}

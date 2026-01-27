package my.mmu.Kaixuanrssnewsreader.ui.setupguide;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.databinding.ActivitySetupGuideBinding;
import my.mmu.Kaixuanrssnewsreader.ui.main.MainActivity;

public class SetupGuideActivity extends AppCompatActivity {

    private static final String TAG = SetupGuideActivity.class.getSimpleName();
    private ActivitySetupGuideBinding binding;
    private VideoView setupGuideVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupGuideBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.setupGuideToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.setup_guide_title);
        }

        setupGuideVideo = binding.setupGuideVideo;

        String videoPath = "android.resource://" + getPackageName() + "/raw/setup_guide_demo";
        setupGuideVideo.setVideoURI(Uri.parse(videoPath));

        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(setupGuideVideo);
        setupGuideVideo.setMediaController(mediaController);

        binding.getApiKeyButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/settings/keys"));
            startActivity(intent);
        });

        binding.getModelButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/models"));
            startActivity(intent);
        });

        binding.solvePrivacyButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/settings/privacy"));
            startActivity(intent);
        });

        binding.goToSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("navigateToSettings", true);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (setupGuideVideo != null && setupGuideVideo.isPlaying()) {
            setupGuideVideo.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (setupGuideVideo != null) {
            setupGuideVideo.stopPlayback();
        }
        binding = null;
    }
}

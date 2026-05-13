package my.mmu.Kaixuanrssnewsreader.ui.main;

import android.app.Application;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;

import java.io.IOException;
import java.net.SocketException;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;

@HiltAndroidApp
public class MainApplication extends Application implements Configuration.Provider {

    @Inject
    HiltWorkerFactory workerFactory;

    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;

    private static final String TAG = "MainApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if (e instanceof IOException || e instanceof SocketException) {
                return;
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
            if (e instanceof NullPointerException || e instanceof IllegalArgumentException) {
                return;
            }
            if (e instanceof IllegalStateException) {
                return;
            }
            Log.e(TAG, "Undeliverable exception", e);
        });
        try {
            WebView.setWebContentsDebuggingEnabled(false);
        } catch (Exception e) {
        }
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build();
    }
}

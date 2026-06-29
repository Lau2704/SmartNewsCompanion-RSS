package my.mmu.Kaixuanrssnewsreader.service.rss;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class RssWorkManager {

    private static final String TAG = "RssWorkManager";
    public static final String WORK_NAME = "rssWork";
    private static final int MIN_INTERVAL_MINUTES = 15;

    private Context context;
    private SharedPreferencesRepository sharedPreferencesRepository;

    @Inject
    public RssWorkManager(@ApplicationContext Context context, SharedPreferencesRepository sharedPreferencesRepository) {
        this.context = context;
        this.sharedPreferencesRepository = sharedPreferencesRepository;
    }

    public void enqueueRssWorker() {
        int interval = sharedPreferencesRepository.getJobPeriodic();

        if (interval <= 0) {
            Log.d(TAG, "Update interval disabled (0). Cancelling any scheduled RssWorker.");
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
            return;
        }

        int minutes = Math.max(MIN_INTERVAL_MINUTES, interval);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(RssWorker.class, minutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
        Log.d(TAG, "RssWorker scheduled every " + minutes + " minutes.");
    }

    public void dequeueRssWorker() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    public boolean isWorkScheduled() {
        try {
            List<WorkInfo> workInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WORK_NAME)
                    .get();

            for (WorkInfo workInfo : workInfos) {
                WorkInfo.State state = workInfo.getState();
                if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING) {
                    return true;
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error checking work state.", e);
        }
        return false;
    }
}

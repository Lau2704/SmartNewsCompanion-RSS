package my.mmu.Kaixuanrssnewsreader.service.util;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ModelDownloadService extends Service {

    public static final String TAG = "ModelDownloadService";
    public static final String ACTION_COMPLETE = "my.mmu.Kaixuanrssnewsreader.MODEL_DOWNLOAD_COMPLETE";
    public static final String ACTION_FAILED = "my.mmu.Kaixuanrssnewsreader.MODEL_DOWNLOAD_FAILED";

    private ModelDownloadNotification notificationHelper;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new ModelDownloadNotification(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ModelDownloadNotification.NOTIFICATION_ID,
                notificationHelper.buildProgressNotification(LocalModelDownloader.getCurrentProgress()));

        if (LocalModelDownloader.isDownloading()) {
            return START_NOT_STICKY;
        }

        compositeDisposable.add(
                LocalModelDownloader.downloadModel(this, progress -> {
                    notificationHelper.showProgressNotification(progress);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(file -> {
                    String size = LocalModelDownloader.formatFileSize(file.length());
                    notificationHelper.showCompletionNotification(size);
                    stopForeground(STOP_FOREGROUND_DETACH);
                    stopSelf();
                    sendBroadcast(new Intent(ACTION_COMPLETE));
                }, error -> {
                    Log.e(TAG, "Download failed", error);
                    if ("Download cancelled".equals(error.getMessage())) {
                        notificationHelper.cancelNotification();
                    } else {
                        notificationHelper.showFailedNotification(error.getMessage());
                    }
                    stopForeground(STOP_FOREGROUND_DETACH);
                    stopSelf();
                    Intent failIntent = new Intent(ACTION_FAILED);
                    failIntent.putExtra("error", error.getMessage());
                    sendBroadcast(failIntent);
                })
        );

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        compositeDisposable.dispose();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

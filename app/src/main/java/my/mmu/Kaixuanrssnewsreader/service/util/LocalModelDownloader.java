package my.mmu.Kaixuanrssnewsreader.service.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LocalModelDownloader {

    private static final String TAG = "LocalModelDownloader";
    private static final String MODEL_URL = "https://huggingface.co/bartowski/google_gemma-4-E2B-it-GGUF/resolve/main/google_gemma-4-E2B-it-IQ2_M.gguf";
    private static final String MODEL_DIR = "models";
    private static final String MODEL_FILENAME = "google_gemma-4-E2B-it-IQ2_M.gguf";

    private static volatile boolean paused = false;
    private static volatile boolean cancelled = false;
    private static volatile boolean downloading = false;
    private static volatile int currentProgress = 0;

    public static int getCurrentProgress() {
        return currentProgress;
    }

    public static File getModelFile(Context context) {
        return new File(new File(context.getFilesDir(), MODEL_DIR), MODEL_FILENAME);
    }

    public static boolean isModelDownloaded(Context context) {
        File file = getModelFile(context);
        return file.exists() && file.length() > 1_000_000;
    }

    public static long getDownloadedSize(Context context) {
        File file = getModelFile(context);
        return file.exists() ? file.length() : 0;
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static void deleteModel(Context context) {
        File file = getModelFile(context);
        if (file.exists()) {
            if (file.delete()) {
                Log.d(TAG, "Model deleted: " + file.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to delete model: " + file.getAbsolutePath());
            }
        }
    }

    public static boolean isDownloading() {
        return downloading;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static void pauseDownload() {
        paused = true;
    }

    public static void resumeDownload() {
        paused = false;
    }

    public static void cancelDownload(Context context) {
        cancelled = true;
        paused = false;
        File tempFile = new File(new File(context.getFilesDir(), MODEL_DIR), MODEL_FILENAME + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    public static Single<File> downloadModel(Context context, Consumer<Integer> progressCallback) {
        return Single.<File>create(emitter -> {
            downloading = true;
            paused = false;
            cancelled = false;

            File modelDir = new File(context.getFilesDir(), MODEL_DIR);
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }

            File outputFile = new File(modelDir, MODEL_FILENAME);
            File tempFile = new File(modelDir, MODEL_FILENAME + ".tmp");

            if (outputFile.exists() && outputFile.length() > 1_000_000) {
                Log.d(TAG, "Model already downloaded");
                downloading = false;
                emitter.onSuccess(outputFile);
                return;
            }

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(MODEL_URL)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    downloading = false;
                    if (!emitter.isDisposed()) emitter.onError(new IOException("Download failed with code: " + response.code()));
                    return;
                }

                long contentLength = response.body().contentLength();
                Log.d(TAG, "Starting download, size: " + formatFileSize(contentLength));

                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[8192];
                    long totalRead = 0;
                    int bytesRead;
                    int lastProgress = -1;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        while (paused && !cancelled) {
                            Thread.sleep(500);
                        }

                        if (cancelled) {
                            fos.close();
                            if (tempFile.exists()) {
                                tempFile.delete();
                            }
                            downloading = false;
                            if (!emitter.isDisposed()) emitter.onError(new IOException("Download cancelled"));
                            return;
                        }

                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        if (contentLength > 0) {
                            int progress = (int) ((totalRead * 100) / contentLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                currentProgress = progress;
                                if (progressCallback != null) {
                                    progressCallback.accept(progress);
                                }
                            }
                        }
                    }

                    fos.flush();
                }

                if (cancelled) {
                    downloading = false;
                    if (!emitter.isDisposed()) emitter.onError(new IOException("Download cancelled"));
                    return;
                }

                if (!tempFile.renameTo(outputFile)) {
                    downloading = false;
                    if (!emitter.isDisposed()) emitter.onError(new IOException("Failed to rename temp file"));
                    return;
                }

                Log.d(TAG, "Model downloaded successfully: " + formatFileSize(outputFile.length()));
                downloading = false;
                currentProgress = 100;
                emitter.onSuccess(outputFile);

            } catch (Exception e) {
                downloading = false;
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                Log.e(TAG, "Download error", e);
                if (!emitter.isDisposed()) emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }
}

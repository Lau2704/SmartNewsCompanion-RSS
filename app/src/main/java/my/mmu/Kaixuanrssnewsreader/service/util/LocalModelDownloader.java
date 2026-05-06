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
    private static final String MODEL_URL = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf";
    private static final String MODEL_DIR = "models";
    private static final String MODEL_FILENAME = "gemma-2-2b-it-Q4_K_M.gguf";

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

    public static Single<File> downloadModel(Context context, Consumer<Integer> progressCallback) {
        return Single.<File>create(emitter -> {
            File modelDir = new File(context.getFilesDir(), MODEL_DIR);
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }

            File outputFile = new File(modelDir, MODEL_FILENAME);
            File tempFile = new File(modelDir, MODEL_FILENAME + ".tmp");

            if (outputFile.exists() && outputFile.length() > 1_000_000) {
                Log.d(TAG, "Model already downloaded");
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
                    emitter.onError(new IOException("Download failed with code: " + response.code()));
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
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        if (contentLength > 0) {
                            int progress = (int) ((totalRead * 100) / contentLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                if (progressCallback != null) {
                                    progressCallback.accept(progress);
                                }
                            }
                        }
                    }

                    fos.flush();
                }

                if (!tempFile.renameTo(outputFile)) {
                    emitter.onError(new IOException("Failed to rename temp file"));
                    return;
                }

                Log.d(TAG, "Model downloaded successfully: " + formatFileSize(outputFile.length()));
                emitter.onSuccess(outputFile);

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                emitter.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }
}

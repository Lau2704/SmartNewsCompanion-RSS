package my.mmu.Kaixuanrssnewsreader.service.util;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.nehuatl.llamacpp.LlamaContext;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class LocalLlmEngine {

    private static final String TAG = "LocalLlmEngine";
    private static final String MODEL_DIR = "models";
    private static final String MODEL_FILENAME = "google_gemma-4-E2B-it-IQ2_M.gguf";
    private static final int CONTEXT_LENGTH = 4096;
    private static final int THREADS = 4;
    private static final int BATCH = 512;
    private static final int INFERENCE_STEPS = 4096;
    private static final long INFERENCE_TIMEOUT_MS = 300_000L;

    private static volatile boolean nativeLoaded = false;
    private static volatile boolean nativeAvailable = false;

    private final Context context;
    private volatile LlamaContext llamaContext;
    private volatile boolean modelLoaded = false;

    @Inject
    public LocalLlmEngine(@ApplicationContext Context context) {
        this.context = context;
    }

    private static synchronized boolean ensureNativeLoaded() {
        if (nativeLoaded) return nativeAvailable;
        nativeLoaded = true;
        try {
            System.loadLibrary("rnllama");
            nativeAvailable = true;
            Log.d(TAG, "Native library loaded successfully");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load native library", e);
            nativeAvailable = false;
        }
        return nativeAvailable;
    }

    public File getModelFile() {
        return new File(new File(context.getFilesDir(), MODEL_DIR), MODEL_FILENAME);
    }

    public boolean isModelDownloaded() {
        File file = getModelFile();
        return file.exists() && file.length() > 1_000_000;
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    public synchronized void loadModel() {
        if (!ensureNativeLoaded()) {
            throw new IllegalStateException("Native library not available. The local model feature is not supported on this device.");
        }
        if (modelLoaded && llamaContext != null) {
            Log.d(TAG, "Model already loaded");
            return;
        }

        File modelFile = getModelFile();
        if (!modelFile.exists()) {
            throw new IllegalStateException("Model file not found. Please download the model first from Settings.");
        }

        Log.d(TAG, "Loading model from: " + modelFile.getAbsolutePath());

        LlamaContext ctx = new LlamaContext();

        long handle;
        ParcelFileDescriptor pfd = null;
        try {
            pfd = ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY);
            int fd = pfd.detachFd();
            Log.d(TAG, "Opened model FD: " + fd);
            handle = ctx.initContextWithFd(
                    fd,
                    true,
                    CONTEXT_LENGTH,
                    BATCH,
                    THREADS,
                    THREADS,
                    false,
                    false,
                    false,
                    "",
                    0.8f,
                    0.95f,
                    40,
                    0,
                    null
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open model file: " + e.getMessage(), e);
        }

        if (handle == 0) {
            throw new IllegalStateException("Failed to initialize llama context. The model may be corrupted or incompatible.");
        }

        ctx.setContextHandle(handle);
        llamaContext = ctx;
        modelLoaded = true;
        Log.d(TAG, "Model loaded successfully, handle: " + handle);
    }

    public synchronized String complete(String systemPrompt, String userMessage) {
        if (!ensureNativeLoaded()) {
            throw new IllegalStateException("Native library not available");
        }
        if (!modelLoaded || llamaContext == null) {
            throw new IllegalStateException("Model not loaded. Call loadModel() first.");
        }

        String prompt = "<start_of_turn>system\n" + systemPrompt + "<end_of_turn>\n"
                + "<start_of_turn>user\n" + userMessage + "<end_of_turn>\n"
                + "<start_of_turn>model\n";

        StringBuilder result = new StringBuilder();
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicBoolean errorFlag = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>("");

        LlamaContext ctx = llamaContext;
        LlamaContext.PartialCompletionCallback partialCallback = new LlamaContext.PartialCompletionCallback(ctx, true);

        ctx.setCallback(token -> {
            if (token.contains("<end_of_turn>") || token.contains("</s>")) {
                completionLatch.countDown();
            } else {
                result.append(token);
            }
        });

        try {
            Thread inferenceThread = new Thread(() -> {
                try {
                    String[] stopTokens = new String[]{"<end_of_turn>", "</s>"};
                    ctx.doCompletion(
                            ctx.getContextHandle(),
                            prompt,
                            "",
                            0.8f,
                            INFERENCE_STEPS,
                            THREADS,
                            BATCH,
                            BATCH,
                            0.95f,
                            40,
                            0.05f,
                            1.0f,
                            0.0f,
                            1.1f,
                            true,
                            0,
                            5.0f,
                            0.1f,
                            1.0f,
                            1.0f,
                            0.0f,
                            0.0f,
                            0,
                            stopTokens,
                            true,
                            null,
                            null,
                            partialCallback
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Inference error", e);
                    errorFlag.set(true);
                    errorMessage.set(e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
            inferenceThread.setDaemon(true);
            inferenceThread.start();

            boolean completed = completionLatch.await(INFERENCE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!completed) {
                ctx.stopCompletion();
                if (result.length() == 0) {
                    throw new RuntimeException("Inference timed out after " + (INFERENCE_TIMEOUT_MS / 1000) + "s");
                }
            }

            if (errorFlag.get() && result.length() == 0) {
                throw new RuntimeException("Inference failed: " + errorMessage.get());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ctx.stopCompletion();
            if (result.length() == 0) {
                throw new RuntimeException("Inference interrupted", e);
            }
        } finally {
            ctx.setCallback(null);
        }

        return result.toString()
                .replace("<end_of_turn>", "")
                .replace("</s>", "")
                .trim();
    }

    public boolean isReady() {
        return nativeAvailable && isModelDownloaded();
    }

    public void unloadModel() {
        if (llamaContext != null) {
            llamaContext.release();
            llamaContext = null;
        }
        modelLoaded = false;
        Log.d(TAG, "Model unloaded");
    }
}

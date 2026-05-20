package org.nehuatl.llamacpp;

import java.util.List;
import java.util.Map;

public class LlamaContext {

    public interface CompletionCallback {
        void onToken(String token);
    }

    private volatile long contextHandle = 0;
    private CompletionCallback callback;

    public native long initContextWithFd(int fd, boolean mmap, int contextLength, int batch,
                                         int threads, int threadsBatch, boolean rob, boolean mlock,
                                         boolean grammar, String lora, float temp, float topP,
                                         float topK, int extraParam, int[] extraArray);

    public native Map<String, Object> doCompletion(long handle, String prompt, String grammar,
                                                    float temp, int steps, int threads, int batch,
                                                    int ctxBatch, float topP, float topK, float minP,
                                                    float typicalP, float penaltyFreq, float penaltyRepeat,
                                                    boolean penalizeNL, int mirostat, float mirostatTau,
                                                    float mirostatEta, float tailFreeZ,
                                                    float temperatureLastDyn, float dynArange,
                                                    float dynDelta, int topo, String[] stopTokens,
                                                    boolean stream, double[][] logitBias,
                                                    int[] tokenIds, PartialCompletionCallback partialCallback);

    public native void stopCompletion(long handle);
    public native boolean isPredicting(long handle);
    public native void freeContext(long handle);

    public void setCallback(CompletionCallback callback) {
        this.callback = callback;
    }

    public long getContextHandle() {
        return contextHandle;
    }

    public void setContextHandle(long handle) {
        this.contextHandle = handle;
    }

    public void onPartialCompletion(Map<String, Object> result) {
        if (callback != null && result != null) {
            Object token = result.get("token");
            if (token instanceof String) {
                callback.onToken((String) token);
            }
        }
    }

    public boolean isPredicting() {
        return contextHandle != 0 && isPredicting(contextHandle);
    }

    public void stopCompletion() {
        if (contextHandle != 0) {
            stopCompletion(contextHandle);
        }
    }

    public void release() {
        if (contextHandle != 0) {
            freeContext(contextHandle);
            contextHandle = 0;
        }
    }

    public static class PartialCompletionCallback {
        private final boolean emitNeeded;
        private final LlamaContext context;

        public PartialCompletionCallback(LlamaContext context, boolean emitNeeded) {
            this.context = context;
            this.emitNeeded = emitNeeded;
        }

        public void onPartialCompletion(Map<String, Object> result) {
            if (emitNeeded && context != null) {
                context.onPartialCompletion(result);
            }
        }
    }
}

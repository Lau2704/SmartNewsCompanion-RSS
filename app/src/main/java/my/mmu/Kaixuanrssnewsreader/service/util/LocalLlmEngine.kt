package my.mmu.Kaixuanrssnewsreader.service.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocalLlmEngine"
        private const val MODEL_DIR = "models"
        private const val MODEL_FILENAME = "google_gemma-4-E2B-it-IQ2_M.gguf"
        private const val CONTEXT_LENGTH = 4096
        private const val INFERENCE_TIMEOUT_MS = 300_000L

        init {
            System.loadLibrary("rnllama")
        }
    }

    private var llamaHelper: LlamaHelper? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var modelLoaded = false

    fun getModelFile(): File {
        return File(File(context.filesDir, MODEL_DIR), MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > 1_000_000
    }

    fun isModelLoaded(): Boolean = modelLoaded

    fun loadModel() {
        if (modelLoaded && llamaHelper != null) {
            Log.d(TAG, "Model already loaded")
            return
        }

        val modelFile = getModelFile()
        if (!modelFile.exists()) {
            throw IllegalStateException("Model file not found: ${modelFile.absolutePath}")
        }

        Log.d(TAG, "Loading model from: ${modelFile.absolutePath}")

        val helper = LlamaHelper(scope)

        runBlocking {
            withTimeoutOrNull(60_000L) {
                helper.load(modelFile.absolutePath, CONTEXT_LENGTH)
            } ?: throw IllegalStateException("Model loading timed out")
        }

        llamaHelper = helper
        modelLoaded = true
        Log.d(TAG, "Model loaded successfully")
    }

    fun complete(systemPrompt: String, userMessage: String): String {
        if (!modelLoaded || llamaHelper == null) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }

        val helper = llamaHelper!!

        val prompt = "<start_of_turn>system\n$systemPrompt<end_of_turn>\n<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"

        val result = StringBuilder()

        runBlocking {
            val collectorFlow = helper.setCollector()

            withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                helper.predict(prompt, false)

                collectorFlow.collect { token ->
                    result.append(token)
                    if (token.contains("</s>") || token.contains("<end_of_turn>")) {
                        return@collect
                    }
                }
            } ?: run {
                if (result.isEmpty()) {
                    throw Exception("Inference timed out after ${INFERENCE_TIMEOUT_MS / 1000}s")
                }
            }

            helper.unsetCollector()
        }

        return result.toString()
            .replace("<end_of_turn>", "")
            .replace("</s>", "")
            .trim()
    }

    fun unloadModel() {
        llamaHelper?.release()
        llamaHelper = null
        modelLoaded = false
        Log.d(TAG, "Model unloaded")
    }
}

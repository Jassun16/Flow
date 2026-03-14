package com.jassun16.flow.util

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class SummarizerReadyState {
    object Ready                          : SummarizerReadyState()
    object Downloading                    : SummarizerReadyState()
    object Unavailable                    : SummarizerReadyState()
    data class Error(val message: String) : SummarizerReadyState()
}

@Singleton
class GeminiNanoSummarizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_FILENAME = "gemma3-1b-it-int4.bin"
        private const val MODEL_URL      =
            "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma3-1b-it-int4/float32/1/gemma3-1b-it-int4.bin"
    }

    private val mutex                        = Mutex()
    private var llmInference: LlmInference?  = null
    private var llmSession: LlmInferenceSession? = null

    // ── Model file path in app private storage ────────────────────────────
    private val modelFile: File
        get() = File(context.filesDir, MODEL_FILENAME)

    // ── Check if model is already downloaded ──────────────────────────────
    fun isModelDownloaded(): Boolean =
        modelFile.exists() && modelFile.length() > 100_000_000L

    // ── Download model with progress (0.0–1.0) ────────────────────────────
    suspend fun downloadModel(
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request  = okhttp3.Request.Builder().url(MODEL_URL).build()
            val client   = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // no read timeout for large file
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                android.util.Log.e("GeminiNano", "Download HTTP ${response.code}: ${response.message}")
                return@withContext false
            }

            val body       = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            val tmpFile    = File(context.filesDir, "$MODEL_FILENAME.tmp")
            var downloaded = 0L

            body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (totalBytes > 0) onProgress(downloaded.toFloat() / totalBytes)
                    }
                }
            }
            tmpFile.renameTo(modelFile)
            true
        } catch (e: Exception) {
            android.util.Log.e("GeminiNano", "Model download failed", e)
            false
        }
    }

    // ── Load engine + session (mutex-protected — never two instances) ──────
    private suspend fun getOrCreateSession(): LlmInferenceSession {
        return mutex.withLock {
            llmSession ?: run {
                val inferenceOptions = LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .setMaxTopK(40)   // ← max allowed topK ceiling on engine level
                    .build()

                val engine = LlmInference.createFromOptions(context, inferenceOptions)
                    .also { llmInference = it }

                val sessionOptions = LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.2f)
                    .build()

                LlmInferenceSession.createFromOptions(engine, sessionOptions)
                    .also { llmSession = it }
            }
        }
    }

    // ── Status check ──────────────────────────────────────────────────────
    suspend fun checkAndPrepare(): SummarizerReadyState {
        return withContext(Dispatchers.IO) {
            try {
                if (!isModelDownloaded()) return@withContext SummarizerReadyState.Downloading
                getOrCreateSession()
                SummarizerReadyState.Ready
            } catch (e: Exception) {
                android.util.Log.e("GeminiNano", "checkAndPrepare failed", e)
                SummarizerReadyState.Error(e.message ?: "Failed to load model")
            }
        }
    }

    // ── Streaming inference ───────────────────────────────────────────────
    fun summarize(plainText: String): Flow<String> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                val session  = getOrCreateSession()
                val safeText = plainText.take(3000)
                val prompt   = """<start_of_turn>user
Summarize the following article in exactly 3 concise bullet points.
Each bullet must start with •
Be factual and direct. No introduction, no conclusion.

Article:
$safeText
<end_of_turn>
<start_of_turn>model
""".trimIndent()

                // Session API: add query chunk first, then generate
                session.addQueryChunk(prompt)
                session.generateResponseAsync { partialResult: String, done: Boolean ->
                    if (partialResult.isNotEmpty()) trySend(partialResult)
                    if (done) close()
                }
            } catch (e: Exception) {
                android.util.Log.e("GeminiNano", "Inference failed", e)
                close(e)
            }
        }
        awaitClose { }
    }

    // ── Only close on ViewModel cleared — never between articles ──────────
    fun close() {
        llmSession?.close()
        llmSession   = null
        llmInference?.close()
        llmInference = null
    }
}
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
        private const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
    }

    private val mutex = Mutex()
    private var llmInference: LlmInference? = null

    // Track active session so close() can safely tear it down before closing the engine
    @Volatile
    private var currentSession: LlmInferenceSession? = null

    private val modelFile: File
        get() = File(context.filesDir, MODEL_FILENAME)

    fun isModelDownloaded(): Boolean =
        modelFile.exists() && modelFile.length() > 100_000_000L

    private fun copyModelFromDownloads(): Boolean {
        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val sourceFile = java.io.File(downloadsDir, MODEL_FILENAME)
            android.util.Log.d("GeminiNano", "Looking for model at: ${sourceFile.absolutePath}")
            android.util.Log.d("GeminiNano", "Source exists: ${sourceFile.exists()}, size: ${sourceFile.length()}")

            if (!sourceFile.exists()) {
                android.util.Log.e("GeminiNano", "Model not found in Downloads")
                return false
            }

            sourceFile.copyTo(modelFile, overwrite = true)
            android.util.Log.d("GeminiNano", "Model copied! New size: ${modelFile.length()}")
            true
        } catch (e: Exception) {
            android.util.Log.e("GeminiNano", "Copy failed", e)
            false
        }
    }

    private suspend fun getOrCreateInference(): LlmInference {
        return mutex.withLock {
            llmInference ?: withContext(Dispatchers.IO) {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(2048)
                    .setMaxTopK(40)
                    .build()
                LlmInference.createFromOptions(context, options)
                    .also { llmInference = it }
            }
        }
    }

    suspend fun checkAndPrepare(): SummarizerReadyState {
        return withContext(Dispatchers.IO) {
            android.util.Log.d("GeminiNano", "checkAndPrepare() — model exists: ${modelFile.exists()}, size: ${modelFile.length()}")
            try {
                if (!isModelDownloaded()) {
                    val copied = copyModelFromDownloads()
                    if (!copied || !isModelDownloaded()) {
                        return@withContext SummarizerReadyState.Unavailable
                    }
                }
                getOrCreateInference()
                SummarizerReadyState.Ready
            } catch (e: Exception) {
                android.util.Log.e("GeminiNano", "checkAndPrepare failed", e)
                SummarizerReadyState.Error(e.message ?: "Failed to load model")
            }
        }
    }

    fun summarize(plainText: String): Flow<String> = callbackFlow {
        val inference = getOrCreateInference()

        val prompt = """
            <start_of_turn>user
            Summarize the following article in exactly 3 concise bullet points.
            Each bullet must start with •
            Be factual and direct. No introduction, no conclusion.

            Article:
            ${plainText.take(3000)}
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()

        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.2f)
            .build()

        val session = withContext(Dispatchers.IO) {
            LlmInferenceSession.createFromOptions(inference, sessionOptions)
                .also { currentSession = it }  // track for safe cleanup
        }

        withContext(Dispatchers.IO) {
            session.addQueryChunk(prompt)
            session.generateResponseAsync { partialResult: String, done: Boolean ->
                android.util.Log.d("GeminiNano", "Token: '${partialResult.take(20)}' done=$done")
                if (partialResult.isNotEmpty()) trySend(partialResult)
                if (done) close()  // ← channel close ONLY — no session.close() here
            }
        }

        // Session is always closed here — whether flow completes, errors, or is cancelled
        awaitClose {
            try {
                currentSession?.close()
                currentSession = null
            } catch (_: Exception) { }
        }
    }

    // Called from ViewModel.onCleared() — session is always closed first via awaitClose
    fun close() {
        // Close active session first before engine — prevents native crash
        try {
            currentSession?.close()
            currentSession = null
        } catch (_: Exception) { }
        llmInference?.close()
        llmInference = null
    }
}
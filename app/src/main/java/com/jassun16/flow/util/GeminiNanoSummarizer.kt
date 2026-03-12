package com.jassun16.flow.util

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class SummarizerReadyState {
    object Ready       : SummarizerReadyState()
    object Downloading : SummarizerReadyState()
    object Unavailable : SummarizerReadyState()
    data class Error(val message: String) : SummarizerReadyState()
}

@Singleton
class GeminiNanoSummarizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var summarizer: Summarizer? = null

    private fun getOrCreateSummarizer(): Summarizer {
        return summarizer ?: run {
            val options = SummarizerOptions.builder(context)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLanguage(SummarizerOptions.Language.ENGLISH)
                .build()
            Summarization.getClient(options).also { summarizer = it }
        }
    }

    /**
     * Checks feature status on IO thread using blocking .get() on ListenableFuture.
     * Triggers download if DOWNLOADABLE — returns Downloading so ViewModel shows message.
     */
    suspend fun checkAndPrepare(): SummarizerReadyState {
        return withContext(Dispatchers.IO) {
            try {
                val client = getOrCreateSummarizer()
                val status = client.checkFeatureStatus().get()   // blocking — safe on IO
                when (status) {
                    FeatureStatus.AVAILABLE    -> SummarizerReadyState.Ready
                    FeatureStatus.DOWNLOADABLE -> {
                        // Kick off download — inference will auto-run after
                        client.downloadFeature(object : DownloadCallback {
                            override fun onDownloadStarted(bytesToDownload: Long) {}
                            override fun onDownloadFailed(e: GenAiException) {}
                            override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                            override fun onDownloadCompleted() {}
                        })
                        SummarizerReadyState.Downloading
                    }
                    FeatureStatus.DOWNLOADING  -> SummarizerReadyState.Downloading
                    FeatureStatus.UNAVAILABLE  -> SummarizerReadyState.Unavailable
                    else -> SummarizerReadyState.Error("Unknown status: $status")
                }
            } catch (e: Exception) {
                SummarizerReadyState.Error(e.message ?: "Status check failed")
            }
        }
    }

    /**
     * Streams summary tokens as they generate.
     * Text is truncated to 16000 chars (~3000 words) — no auto-truncation in Android API.
     */
    fun summarize(plainText: String): Flow<String> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                val client   = getOrCreateSummarizer()
                val safeText = plainText.take(16_000)   // manual truncation
                val request  = SummarizationRequest.builder(safeText).build()

                client.runInference(request) { partialResult ->
                    trySend(partialResult)
                }.get()   // blocks IO thread until inference completes

                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose { }
    }

    fun close() {
        summarizer?.close()
        summarizer = null
    }
}

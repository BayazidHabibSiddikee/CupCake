package com.cupcake.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    data class GgufModelInfo(
        val fileName: String,
        val displayName: String,
        val tag: String,
        val description: String,
        val sizeBytes: Long,
        val downloadUrl: String? = null,
        val isBundled: Boolean = false
    )

    data class DownloadProgress(
        val fileName: String,
        val progressPct: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val isDownloading: Boolean = true,
        val error: String? = null
    )

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val PREFS_NAME = "cupcake_model_prefs"
        private const val KEY_ACTIVE_MODEL = "active_local_model"
        const val DEFAULT_MODEL = "liltii-v0.2-small-q2_k.gguf"

        val CATALOG = listOf(
            GgufModelInfo(
                fileName = "liltii-v0.2-small-q2_k.gguf",
                displayName = "LilTii v0.2 (Qwen 0.5B)",
                tag = "Custom • Banglish + Emotions",
                description = "Fine-tuned on Banglish emotion-tagged data. Emits [emotion] *action* responses.",
                sizeBytes = 338_000_000L,
                downloadUrl = null,
                isBundled = true
            ),
            GgufModelInfo(
                fileName = "bangla-smollm-135m-v3-q2_k.gguf",
                displayName = "Bangla SmolLM 135M",
                tag = "Custom • Bangla Native",
                description = "Fine-tuned SmolLM 135M on Bangla/Banglish. Ultra-fast, tiny (~15 MB).",
                sizeBytes = 15_000_000L,
                downloadUrl = null,
                isBundled = true
            )
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeModel = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    )
    val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getModelsDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isModelInstalled(fileName: String): Boolean {
        // Check if bundled in assets
        val catalogItem = CATALOG.find { it.fileName == fileName }
        if (catalogItem?.isBundled == true) return true

        // Check if exists in filesDir/models and is non-empty
        val file = File(getModelsDir(), fileName)
        return file.exists() && file.length() > 1_000_000L
    }

    fun getInstalledModels(): List<GgufModelInfo> {
        return CATALOG.filter { isModelInstalled(it.fileName) }
    }

    fun setActiveModel(fileName: String) {
        if (!isModelInstalled(fileName)) {
            Log.w(TAG, "Cannot activate model $fileName: not installed")
            return
        }
        prefs.edit().putString(KEY_ACTIVE_MODEL, fileName).apply()
        _activeModel.value = fileName
        Log.i(TAG, "Active local model set to: $fileName")
    }

    fun startDownload(model: GgufModelInfo) {
        val url = model.downloadUrl ?: return
        val fileName = model.fileName

        if (downloadJobs[fileName]?.isActive == true) {
            Log.i(TAG, "Download already in progress for $fileName")
            return
        }

        val job = scope.launch {
            val targetFile = File(getModelsDir(), fileName)
            val tempFile = File(getModelsDir(), "$fileName.tmp")

            _downloadProgress.value = _downloadProgress.value + (fileName to DownloadProgress(
                fileName = fileName,
                progressPct = 0,
                downloadedBytes = 0L,
                totalBytes = model.sizeBytes,
                isDownloading = true
            ))

            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "CupCake-Android/1.0")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength().let { if (it > 0) it else model.sizeBytes }

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(1024 * 1024) // 1MB buffer
                            var bytesReadTotal = 0L
                            var lastReportBytes = 0L

                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesReadTotal += read

                                if (bytesReadTotal - lastReportBytes >= 4L * 1024 * 1024 || bytesReadTotal == contentLength) {
                                    lastReportBytes = bytesReadTotal
                                    val pct = if (contentLength > 0) {
                                        ((bytesReadTotal * 100) / contentLength).toInt().coerceIn(0, 100)
                                    } else 0

                                    _downloadProgress.value = _downloadProgress.value + (fileName to DownloadProgress(
                                        fileName = fileName,
                                        progressPct = pct,
                                        downloadedBytes = bytesReadTotal,
                                        totalBytes = contentLength,
                                        isDownloading = true
                                    ))
                                }
                            }
                        }
                    }

                    // Rename temp to target
                    if (targetFile.exists()) targetFile.delete()
                    if (tempFile.renameTo(targetFile)) {
                        _downloadProgress.value = _downloadProgress.value + (fileName to DownloadProgress(
                            fileName = fileName,
                            progressPct = 100,
                            downloadedBytes = targetFile.length(),
                            totalBytes = targetFile.length(),
                            isDownloading = false
                        ))
                        Log.i(TAG, "Download completed successfully: ${targetFile.absolutePath}")
                    } else {
                        throw Exception("Failed to rename temporary download file")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $fileName", e)
                tempFile.delete()
                _downloadProgress.value = _downloadProgress.value + (fileName to DownloadProgress(
                    fileName = fileName,
                    progressPct = 0,
                    downloadedBytes = 0L,
                    totalBytes = model.sizeBytes,
                    isDownloading = false,
                    error = e.message ?: "Download failed"
                ))
            } finally {
                downloadJobs.remove(fileName)
            }
        }

        downloadJobs[fileName] = job
    }

    fun cancelDownload(fileName: String) {
        downloadJobs[fileName]?.cancel()
        downloadJobs.remove(fileName)
        val tempFile = File(getModelsDir(), "$fileName.tmp")
        if (tempFile.exists()) tempFile.delete()

        _downloadProgress.value = _downloadProgress.value - fileName
        Log.i(TAG, "Download cancelled for $fileName")
    }

    fun deleteModel(fileName: String): Boolean {
        val model = CATALOG.find { it.fileName == fileName }
        if (model?.isBundled == true) {
            // Cannot delete bundled asset
            return false
        }
        val file = File(getModelsDir(), fileName)
        val deleted = file.delete()
        if (deleted && _activeModel.value == fileName) {
            // Fallback to default
            setActiveModel(DEFAULT_MODEL)
        }
        return deleted
    }
}

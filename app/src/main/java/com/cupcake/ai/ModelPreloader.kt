package com.cupcake.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Loads the active on-device GGUF in the background right after app start so the
 * companion is already warm when the user opens a chat (no first-message wait).
 *
 * The load is memory-mapped and idempotent: if the same model is already resident,
 * [LlamaEngine.loadModel] returns immediately, so this never double-loads.
 */
object ModelPreloader {

    private const val TAG = "ModelPreloader"
    private const val PREFS_NAME = "cupcake_model_prefs"
    private const val KEY_ACTIVE_MODEL = "active_local_model"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    fun preload(context: Context) {
        if (started) return
        started = true

        scope.launch {
            // Let the first frame settle before touching a few hundred MB of weights.
            delay(2_500)

            val appContext = context.applicationContext
            try {
                LlamaEngine.initialize(appContext)

                val activeModel = ModelDownloadManager.resolveStoredActiveModel(
                    appContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_ACTIVE_MODEL, null)
                )

                // Skip if the model file is not present yet (will be copied on demand).
                val modelFile = File(File(appContext.filesDir, "models"), activeModel)
                val bundledInAssets = runCatching {
                    appContext.assets.open("models/$activeModel").use { }
                    true
                }.getOrDefault(false)
                if (!modelFile.exists() && !bundledInAssets) {
                    Log.i(TAG, "No local model available to preload ($activeModel)")
                    return@launch
                }

                if (LlamaEngine.getInstance().isReady() &&
                    LlamaEngine.getInstance().getLoadedModelName() == activeModel
                ) {
                    Log.i(TAG, "Model already warm: $activeModel")
                    return@launch
                }

                val result = LlamaEngine.getInstance().loadModel(
                    context = appContext,
                    config = LlamaEngine.Config(modelFileName = "models/$activeModel")
                )
                Log.i(TAG, "Preload of $activeModel: ${if (result.isSuccess) "warm" else "failed (${result.exceptionOrNull()?.message})"}")
            } catch (e: Exception) {
                Log.w(TAG, "Preload skipped: ${e.message}")
            }
        }
    }
}

package com.cupcake.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDownloadManagerTest {

    @Test
    fun `missing stored model falls back to default`() {
        assertEquals(ModelDownloadManager.DEFAULT_MODEL, ModelDownloadManager.resolveStoredActiveModel(null))
    }

    @Test
    fun `catalog model is preserved`() {
        assertEquals(
            ModelDownloadManager.DEFAULT_MODEL,
            ModelDownloadManager.resolveStoredActiveModel(ModelDownloadManager.DEFAULT_MODEL)
        )
    }

    @Test
    fun `stale renamed model falls back to default`() {
        assertEquals(
            ModelDownloadManager.DEFAULT_MODEL,
            ModelDownloadManager.resolveStoredActiveModel("bangla-smollm-135m-v0.2-q2_k.gguf")
        )
    }

    @Test
    fun `unknown model falls back to default`() {
        assertEquals(
            ModelDownloadManager.DEFAULT_MODEL,
            ModelDownloadManager.resolveStoredActiveModel("totally-unknown.gguf")
        )
    }
}

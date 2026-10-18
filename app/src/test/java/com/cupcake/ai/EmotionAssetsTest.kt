package com.cupcake.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionAssetsTest {

    private val base = "file:///android_asset/cupcake/emotions"

    @Test
    fun `bracket tag takes priority over lexical detection`() {
        val asset = EmotionAssets.assetForText("[happy] *bounces* you're so cute!")
        assertEquals("$base/happy.png", asset)
    }

    @Test
    fun `sleepy tag maps to sleepy art`() {
        val asset = EmotionAssets.assetForText("[sleepy] *yawns* good morning")
        assertEquals("$base/sleepy.png", asset)
    }

    @Test
    fun `lexical affection maps to happy art`() {
        val asset = EmotionAssets.assetForText("*blushes and looks away* i love you so much")
        assertEquals("$base/happy.png", asset)
    }

    @Test
    fun `neutral text maps to happy art`() {
        assertEquals("$base/happy.png", EmotionAssets.assetForText("the weather is okay today"))
    }

    @Test
    fun `emotion enum maps to expected stems`() {
        assertEquals("$base/angry.png", EmotionAssets.assetFor(EmotionClassifier.Emotion.ANGER))
        assertEquals("$base/sad.png", EmotionAssets.assetFor(EmotionClassifier.Emotion.SADNESS))
        assertEquals("$base/annoyed.png", EmotionAssets.assetFor(EmotionClassifier.Emotion.JEALOUSY))
        assertEquals("$base/surprised.png", EmotionAssets.assetFor(EmotionClassifier.Emotion.SURPRISE))
    }

    @Test
    fun `recommended thread count stays within sane bounds`() {
        val threads = recommendedThreadCount()
        assertTrue("threads=$threads", threads in 2..6)
    }

    @Test
    fun `stem is the shared vocabulary for app and esp`() {
        assertEquals("happy", EmotionAssets.stemForText("[happy] *bounces* hi"))
        assertEquals("happy", EmotionAssets.espFaceForStem("happy"))
        assertEquals("/assets/cupcake/emotions/happy.png", EmotionAssets.httpPathForStem("happy"))
    }

    @Test
    fun `every stem has an esp face and http path`() {
        for (stem in EmotionAssets.ALL_STEMS) {
            assertTrue(EmotionAssets.espFaceForStem(stem).isNotBlank())
            assertTrue(EmotionAssets.httpPathForStem(stem).endsWith("/$stem.png"))
        }
    }
}

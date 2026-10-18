package com.cupcake.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // -- regression tests for the emotion-sync QA pass (P1-2 / P1-4) ----------

    @Test
    fun `raw rich tags pass straight through to the ESP vocabulary`() {
        // Used to be folded to their stem, starving firmware faces that exist.
        assertEquals("heart_eyes", EmotionAssets.espFaceForTag("heart_eyes"))
        assertEquals("wink", EmotionAssets.espFaceForTag("wink"))
        assertEquals("rage", EmotionAssets.espFaceForTag("rage"))
        assertEquals("excited", EmotionAssets.espFaceForTag("excited"))
        assertEquals("confused", EmotionAssets.espFaceForTag("confused"))
    }

    @Test
    fun `neutral tag reaches the ESP wire name not the asset stem`() {
        // ESP vocabulary is "neutral" (firmware drawFace has no "normal" case);
        // the normal<->neutral swap only applies to on-device asset stems.
        assertEquals("neutral", EmotionAssets.espFaceForTag("neutral"))
        assertEquals("neutral", EmotionAssets.espFaceForStem("normal"))
    }

    @Test
    fun `rich legacy tags remap onto real firmware faces`() {
        // stemToEspFace fallback keeps legacy dicts on valid expressions.
        assertEquals("heart_eyes", EmotionAssets.espFaceForTag("lovely"))
        assertEquals("wink", EmotionAssets.espFaceForTag("flirty"))
    }

    @Test
    fun `totally unknown tags fold to a real face`() {
        assertEquals("neutral", EmotionAssets.espFaceForTag("totally-unknown-tag"))
        assertNull(EmotionAssets.espFaceForTag(null))
    }

    @Test
    fun `tagOf extracts first word tag case-insensitively`() {
        assertEquals("happy", EmotionAssets.tagOf("[HAPPY] hello"))
        assertEquals("sleepy", EmotionAssets.tagOf("junk [sLeEpY] more"))
        assertNull(EmotionAssets.tagOf("no tags here"))
        assertNull(EmotionAssets.tagOf("[no-space]"))
    }
}

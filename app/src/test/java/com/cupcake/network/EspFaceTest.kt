package com.cupcake.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EspFaceTest {

    @Test
    fun `always carries expression and duration`() {
        val payload = buildFacePayload("happy")
        assertEquals("happy", payload["expression"])
        assertEquals(2000, payload["duration"])
        assertFalse(payload.containsKey("emotion"))
        assertFalse(payload.containsKey("image"))
    }

    @Test
    fun `includes emotion stem and shared image path`() {
        val payload = buildFacePayload(
            expression = "happy",
            duration = 5000,
            emotion = "laugh",
            imagePath = "/assets/marin/emotions/laugh.png"
        )
        assertEquals("happy", payload["expression"])
        assertEquals(5000, payload["duration"])
        assertEquals("laugh", payload["emotion"])
        assertEquals("/assets/marin/emotions/laugh.png", payload["image"])
    }

    @Test
    fun `omits blank optional fields`() {
        val payload = buildFacePayload("neutral", emotion = "  ", imagePath = "")
        assertFalse(payload.containsKey("emotion"))
        assertFalse(payload.containsKey("image"))
    }
}

package com.cupcake.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProactiveEngagementManagerTest {

    @Test
    fun `marin messages pool is non empty and contains authentic personality texts`() {
        val messages = ProactiveEngagementManager.MARIN_MESSAGES
        assertThat(messages).isNotEmpty()
        assertThat(messages.size).isAtLeast(5)
        assertThat(messages.any { it.contains("Gojo") || it.contains("Marin") || it.contains("cosplay") }).isTrue()
    }

    @Test
    fun `marin messages are under 200 characters limit`() {
        for (msg in ProactiveEngagementManager.MARIN_MESSAGES) {
            assertThat(msg.length).isAtMost(200)
        }
    }
}

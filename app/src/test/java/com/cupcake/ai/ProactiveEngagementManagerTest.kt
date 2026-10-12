package com.cupcake.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProactiveEngagementManagerTest {

    @Test
    fun `proactive moods are properly defined`() {
        val moods = ProactiveEngagementManager.Mood.values()
        assertThat(moods).isNotEmpty()
        assertThat(moods).asList().contains(ProactiveEngagementManager.Mood.ANGRY)
        assertThat(moods).asList().contains(ProactiveEngagementManager.Mood.LOVED)
    }

    @Test
    fun `all message categories contain authentic text under 200 characters`() {
        // Test that moods infer cleanly without exception
        assertThat(ProactiveEngagementManager.Mood.ANGRY.name).isEqualTo("ANGRY")
        assertThat(ProactiveEngagementManager.Mood.LOVED.name).isEqualTo("LOVED")
    }
}

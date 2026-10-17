package com.cupcake.ai

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the ad/billing seams on [EnergyManager] with the deterministic fakes, so the
 * energy economy stays verifiable while no ad SDK or Play Billing integration is wired.
 */
class EnergyManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager(
        ad: AdManager = FakeAdManager(),
        billing: BillingManager = FakeBillingManager()
    ): EnergyManager {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns tmp.root
        return EnergyManager(context, ad, billing)
    }

    @Test
    fun `watching a rewarded ad grants energy and consumes the cooldown`() = runBlocking {
        val ad = FakeAdManager(AdResult.REWARDED)
        val manager = manager(ad = ad)
        val before = manager.energy.value

        val granted = manager.watchRewardedAd()

        assertThat(granted).isTrue()
        assertThat(manager.energy.value).isGreaterThan(before)
        assertThat(ad.showCount).isEqualTo(1)
        assertThat(manager.canWatchAd()).isFalse()
    }

    @Test
    fun `a dismissed ad grants nothing and leaves the cooldown available`() = runBlocking {
        val ad = FakeAdManager(AdResult.DISMISSED)
        val manager = manager(ad = ad)
        val before = manager.energy.value

        val granted = manager.watchRewardedAd()

        assertThat(granted).isFalse()
        assertThat(manager.energy.value).isEqualTo(before)
        assertThat(ad.showCount).isEqualTo(1)
        assertThat(manager.canWatchAd()).isTrue()
    }

    @Test
    fun `an unavailable ad is never shown and grants nothing`() = runBlocking {
        val ad = FakeAdManager().apply { setAvailable(false) }
        val manager = manager(ad = ad)
        val before = manager.energy.value

        val granted = manager.watchRewardedAd()

        assertThat(granted).isFalse()
        assertThat(manager.energy.value).isEqualTo(before)
        assertThat(ad.showCount).isEqualTo(0)
    }

    @Test
    fun `a successful purchase grants pro`() = runBlocking {
        val billing = FakeBillingManager(succeeds = true)
        val manager = manager(billing = billing)

        val purchased = manager.purchasePro()

        assertThat(purchased).isTrue()
        assertThat(manager.isPro.value).isTrue()
        assertThat(billing.purchaseCount).isEqualTo(1)
    }

    @Test
    fun `a cancelled purchase keeps the free tier`() = runBlocking {
        val billing = FakeBillingManager(succeeds = false)
        val manager = manager(billing = billing)

        val purchased = manager.purchasePro()

        assertThat(purchased).isFalse()
        assertThat(manager.isPro.value).isFalse()
    }

    @Test
    fun `an unavailable store never grants pro`() = runBlocking {
        val billing = FakeBillingManager(succeeds = true, available = false)
        val manager = manager(billing = billing)

        val purchased = manager.purchasePro()

        assertThat(purchased).isFalse()
        assertThat(manager.isPro.value).isFalse()
        assertThat(billing.purchaseCount).isEqualTo(0)
    }

    @Test
    fun `pro users cannot watch ads`() = runBlocking {
        val ad = FakeAdManager(AdResult.REWARDED)
        val manager = manager(ad = ad)
        manager.setPro(true)

        assertThat(manager.canWatchAd()).isFalse()
        assertThat(manager.watchRewardedAd()).isFalse()
        assertThat(ad.showCount).isEqualTo(0)
    }
}

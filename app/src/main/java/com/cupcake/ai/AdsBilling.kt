package com.cupcake.ai

/**
 * Result of a rewarded-ad request.
 */
enum class AdResult { REWARDED, DISMISSED, FAILED }

/**
 * Rewarded-ad surface.
 *
 * No ad SDK (AdMob/ironSource/…) is bundled yet, so the app injects [FakeAdManager] and the
 * energy flow is complete and testable. Wiring a real SDK means implementing this interface
 * and changing the Hilt binding — no caller has to change.
 */
interface AdManager {
    /** Whether a rewarded ad can be shown right now (loaded, not on cooldown). */
    fun isRewardedAdAvailable(): Boolean

    /** Present a rewarded ad and suspend until the user finishes or dismisses it. */
    suspend fun showRewardedAd(): AdResult
}

/**
 * Pro-upgrade surface.
 *
 * Real Play Billing is not wired yet ([com.android.billingclient:billing-ktx] is on the
 * classpath but unused); [FakeBillingManager] keeps the UI path working until a
 * `BillingClient` implementation replaces it behind this interface.
 */
interface BillingManager {
    /** Whether the store can be reached on this device. */
    fun isAvailable(): Boolean

    /** Run the Pro purchase flow; true only when the purchase completed. */
    suspend fun purchasePro(): Boolean
}

/** Deterministic rewarded-ad stand-in (defaults to a successful watch). */
class FakeAdManager(
    @Volatile private var result: AdResult = AdResult.REWARDED,
    @Volatile private var available: Boolean = true
) : AdManager {

    @Volatile
    var showCount: Int = 0
        private set

    fun setResult(newResult: AdResult) {
        result = newResult
    }

    fun setAvailable(value: Boolean) {
        available = value
    }

    override fun isRewardedAdAvailable(): Boolean = available

    override suspend fun showRewardedAd(): AdResult {
        showCount++
        return result
    }
}

/** Deterministic billing stand-in (defaults to a successful purchase). */
class FakeBillingManager(
    private val succeeds: Boolean = true,
    private val available: Boolean = true
) : BillingManager {

    @Volatile
    var purchaseCount: Int = 0
        private set

    override fun isAvailable(): Boolean = available

    override suspend fun purchasePro(): Boolean {
        purchaseCount++
        return succeeds
    }
}

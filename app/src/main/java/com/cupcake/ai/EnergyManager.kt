package com.cupcake.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

class EnergyManager(private val context: Context) {

    companion object {
        private const val TAG = "EnergyManager"
        private const val ENERGY_FILE = "energy_state.json"
        const val MAX_ENERGY = 30 * 60 * 1000L // 30 minutes in ms
        const val AD_REWARD_ENERGY = 30 * 60 * 1000L // 30 minutes per ad
        const val ENERGY_PER_MESSAGE = 30 * 1000L // 30 seconds per message
        const val ENERGY_PER_GAME = 60 * 1000L // 1 minute per game
    }

    private val _energy = MutableStateFlow(0L)
    val energy = _energy.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro = _isPro.asStateFlow()

    private val _adAvailable = MutableStateFlow(true)
    val adAvailable = _adAvailable.asStateFlow()

    private val energyFile: File

    init {
        energyFile = File(context.filesDir, ENERGY_FILE)
        loadState()
    }

    private fun loadState() {
        try {
            if (energyFile.exists()) {
                val json = energyFile.readText()
                val state = kotlinx.serialization.json.Json.decodeFromString<EnergyState>(json)
                _energy.value = state.energyRemaining
                _isPro.value = state.isPro
            } else {
                // New user gets welcome energy
                _energy.value = 10 * 60 * 1000L // 10 minutes free
                saveState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load energy state", e)
            _energy.value = 5 * 60 * 1000L // 5 minutes fallback
        }
    }

    private fun saveState() {
        try {
            val state = EnergyState(_energy.value, _isPro.value)
            energyFile.writeText(
                kotlinx.serialization.json.Json.encodeToString(EnergyState.serializer(), state)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save energy state", e)
        }
    }

    fun hasEnergy(): Boolean {
        return _isPro.value || _energy.value > 0
    }

    fun getRemainingMinutes(): Long = _energy.value / (60 * 1000)

    fun getRemainingSeconds(): Long = (_energy.value % (60 * 1000)) / 1000

    fun consumeEnergy(amount: Long = ENERGY_PER_MESSAGE): Boolean {
        if (_isPro.value) return true
        if (_energy.value >= amount) {
            _energy.value -= amount
            saveState()
            return true
        }
        return false
    }

    fun addEnergy(amount: Long) {
        if (!_isPro.value) {
            _energy.value = (_energy.value + amount).coerceAtMost(MAX_ENERGY)
            saveState()
        }
    }

    fun rewardAdWatched() {
        addEnergy(AD_REWARD_ENERGY)
        _adAvailable.value = false
        // Reset ad availability after cooldown
        CoroutineScope(Dispatchers.IO).launch {
            Thread.sleep(5 * 60 * 1000) // 5 min cooldown
            _adAvailable.value = true
        }
    }

    fun setPro(pro: Boolean) {
        _isPro.value = pro
        if (pro) {
            _energy.value = MAX_ENERGY // Unlimited
        }
        saveState()
    }

    fun purchasePro(): Boolean {
        // In real app, this would call Play Billing
        // For now, simulate success
        setPro(true)
        return true
    }

    fun canWatchAd(): Boolean = _adAvailable.value && !_isPro.value

    fun getEnergyPercentage(): Float {
        return if (_isPro.value) 1f else (_energy.value.toFloat() / MAX_ENERGY).coerceIn(0f, 1f)
    }

    @Serializable
    data class EnergyState(
        val energyRemaining: Long,
        val isPro: Boolean
    )
}

// Ad Manager (placeholder for Google AdMob integration)
class AdManager(private val context: Context) {

    companion object {
        private const val TAG = "AdManager"
    }

    interface AdCallback {
        fun onAdLoaded()
        fun onAdFailedToLoad(error: String)
        fun onAdShown()
        fun onAdDismissed()
        fun onUserEarnedReward()
        fun onAdClicked()
    }

    // Rewarded ad for energy
    fun loadRewardedAd(callback: AdCallback) {
        Log.i(TAG, "Loading rewarded ad...")
        // TODO: Implement Google AdMob rewarded ad
        // MobileAds.initialize(context) { }
        // RewardedAd.load(context, "REWARDED_AD_UNIT_ID", AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
        //     override fun onAdLoaded(ad: RewardedAd) { ... }
        //     override fun onAdFailedToLoad(error: LoadAdError) { ... }
        // })
    }

    fun showRewardedAd(callback: AdCallback) {
        Log.i(TAG, "Showing rewarded ad...")
        // TODO: Show loaded rewarded ad
    }

    // Interstitial ad
    fun loadInterstitialAd(callback: AdCallback) {
        // TODO: Implement
    }

    fun showInterstitialAd(callback: AdCallback) {
        // TODO: Implement
    }
}
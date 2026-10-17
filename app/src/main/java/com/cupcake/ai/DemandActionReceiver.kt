package com.cupcake.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles the "Fulfill" / "Ignore" actions on a Marin demand notification, including
 * when the app process was dead and only prefs are available.
 */
class DemandActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(DemandNotifier.EXTRA_DEMAND_ID)
        val demand = id?.let { DemandManager.byId(it) }
        if (demand == null) {
            DemandNotifier.cancel(context)
            return
        }

        when (intent.action) {
            DemandNotifier.ACTION_FULFILL -> {
                val ok = DemandManager.fulfill(context, demand)
                if (ok) {
                    DemandNotifier.notifyResult(context, demand.fulfilledLine)
                } else {
                    DemandNotifier.notifyResult(
                        context,
                        "Not enough points for ${demand.title.replace("Marin ", "my ")}! Win a game and come back~ 🥺"
                    )
                }
            }
            DemandNotifier.ACTION_IGNORE -> {
                DemandManager.dismiss(context, penalize = true, demand = demand)
            }
            else -> Log.w("DemandActionReceiver", "Unknown action ${intent.action}")
        }
    }
}

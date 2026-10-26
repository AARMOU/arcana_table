package com.arcanatable

import org.json.JSONObject

/** Formats only the game-over facts provided by the engine/host; never reconstructs a win from recap text. */
internal object ArgentumOutcomeLog {
    private fun known(value: String?): String? = value?.takeUnless { it.isBlank() || it == "null" || it == "UNKNOWN" }

    fun diagnostic(packet: JSONObject, names: Map<String, String>): String {
        val winnerId = known(packet.optString("winnerId"))
        val winner = known(packet.optString("winnerName")) ?: winnerId?.let { names[it] ?: it } ?: "none"
        val losses = packet.optJSONArray("losers")
        val losers = (0 until (losses?.length() ?: 0)).mapNotNull { index ->
            losses?.optJSONObject(index)?.let { loss ->
                val id = known(loss.optString("playerId"))
                val name = known(loss.optString("playerName")) ?: id?.let { names[it] ?: it } ?: "unknown"
                val reason = known(loss.optString("reason"))
                if (reason == null) name else "$name ($reason)"
            }
        }
        val matchUnfinished = packet.optBoolean("matchUnfinished")
        val parts = mutableListOf(
            if (matchUnfinished) "Argentum human eliminated (match unfinished): winner=none"
                else "Argentum game over: winner=$winner",
            "losers=${losers.ifEmpty { listOf("not supplied") }.joinToString(", ")}",
            "result=${packet.optString("reason", "UNKNOWN")}"
        )
        if (matchUnfinished) {
            parts += "remainingPlayers=${packet.optInt("activePlayersRemaining")}"
            known(packet.optString("eliminatedPlayerName"))?.let { parts += "eliminated=$it" }
            if (!packet.isNull("eliminationLife")) parts += "life=${packet.optInt("eliminationLife")}"
        }
        known(packet.optString("engineReason"))?.let { parts += "engineReason=$it" }
        known(packet.optString("sourceCard"))?.let { parts += "source=$it" }
        known(packet.optString("winCondition"))?.let { parts += "${if (matchUnfinished) "eliminationDetail" else "condition"}=$it" }
        val artifact = known(packet.optString("artifactName"))
        if (artifact != null && !packet.isNull("artifactCount")) {
            parts += "verifiedArtifactGroup=$artifact x${packet.optInt("artifactCount")}"
        }
        return parts.joinToString("; ")
    }

    fun banner(packet: JSONObject, viewerId: String?, names: Map<String, String>): String {
        val explicitMessage = known(packet.optString("message"))
        if (explicitMessage != null) return explicitMessage
        val winnerId = known(packet.optString("winnerId"))
        val title = when {
            winnerId == null -> "No winner."
            viewerId != null && winnerId == viewerId -> "You won."
            else -> "${known(packet.optString("winnerName")) ?: names[winnerId] ?: "Your opponent"} won."
        }
        val evidence = known(packet.optString("winCondition"))
        val reason = known(packet.optString("engineReason"))
            ?: known(packet.optString("reason"))?.replace('_', ' ')?.lowercase()
        val count = known(packet.optString("artifactName"))?.let { artifact ->
            if (packet.isNull("artifactCount")) null else "$artifact x${packet.optInt("artifactCount")}"
        }
        return listOfNotNull(title, evidence ?: reason?.let { "Reason: $it." }, count?.let { "Verified artifacts: $it." }).joinToString(" ")
    }
}

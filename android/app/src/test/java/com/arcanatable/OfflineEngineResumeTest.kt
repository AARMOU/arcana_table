package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Color
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class OfflineEngineResumeTest {
    @Test fun completeArgentumGameStateSurvivesSaveAndRestore() {
        val cards = File("src/main/assets/argentum/por-cards.jsonl")
            .bufferedReader().useLines { EmbeddedEngineTrial.loadCards(it) }
        val registry = CardRegistry().apply { register(cards) }
        val random = Random(11)
        val you = LocalSeat("You", EmbeddedEngineTrial.monoDeck(cards, Color.GREEN, random).cards, null)
        val ai = LocalSeat("Offline AI", EmbeddedEngineTrial.monoDeck(cards, Color.RED, random).cards, null)
        val before = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(registry, you, ai, paceMs = 0) { before.put(it) }
        val saved: JSONObject
        try {
            host.receive(JSONObject().put("type", "connect"))
            host.receive(JSONObject().put("type", "createQuickGameLobby"))
            var ready = false
            repeat(20) {
                if (ready) return@repeat
                val event = before.poll(20, TimeUnit.SECONDS) ?: error("Offline host stopped responding")
                when (event.optString("type")) {
                    "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"))
                    "stateUpdate" -> ready = true
                    "error" -> error(event.getString("message"))
                }
            }
            require(ready) { "No usable engine state" }
            host.receive(JSONObject().put("type", "arcanaSaveOffline"))
            var export: JSONObject? = null
            repeat(30) {
                if (export != null) return@repeat
                val event = before.poll(20, TimeUnit.SECONDS) ?: error("Offline save timed out")
                if (event.optString("type") == "offlineCheckpoint") export = event.getJSONObject("checkpoint")
                else if (event.optString("type") == "error") error(event.getString("message"))
            }
            saved = requireNotNull(export)
            assertEquals("local-ai", saved.getString("mode"))
            assertNotNull(saved.getJSONObject("gameState").getJSONArray("stack"))
        } finally { host.shutdown() }

        val after = LinkedBlockingQueue<JSONObject>()
        val resumed = LocalArgentumHost(registry, you, ai, paceMs = 0,
            resumeCheckpoint = saved.toString()) { after.put(it) }
        try {
            resumed.receive(JSONObject().put("type", "connect"))
            resumed.receive(JSONObject().put("type", "createQuickGameLobby"))
            var seen = false
            repeat(20) {
                if (seen) return@repeat
                val event = after.poll(20, TimeUnit.SECONDS) ?: error("Restored host stopped responding")
                when (event.optString("type")) {
                    "stateUpdate" -> seen = true
                    "mulliganDecision" -> seen = true
                    "error" -> error(event.getString("message"))
                }
            }
            require(seen) { "No resumed state sent to client" }
            resumed.receive(JSONObject().put("type", "arcanaSaveOffline"))
            var restored: JSONObject? = null
            repeat(20) {
                if (restored != null) return@repeat
                val event = after.poll(20, TimeUnit.SECONDS) ?: error("Restored checkpoint timed out")
                if (event.optString("type") == "offlineCheckpoint") restored = event.getJSONObject("checkpoint")
                else if (event.optString("type") == "error") error(event.getString("message"))
            }
            assertEquals(saved.getJSONObject("gameState").toString(),
                requireNotNull(restored).getJSONObject("gameState").toString())
            assertEquals(saved.getJSONArray("seats").toString(),
                requireNotNull(restored).getJSONArray("seats").toString())
        } finally { resumed.shutdown() }
    }
}

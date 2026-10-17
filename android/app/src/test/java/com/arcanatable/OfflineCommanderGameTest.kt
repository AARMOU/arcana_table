package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.OfflineCardLibrary
import com.arcanatable.engine.OfflineGameSetup
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** With the full library on board, an imported Commander deck plays a premade deck offline. */
class OfflineCommanderGameTest {
    private val assets = File("src/main/assets")

    @Test fun commanderDeckPlaysOffline() {
        val lib = OfflineCardLibrary({ path -> File(assets, path).takeIf { it.exists() }?.inputStream() })
        assumeTrue(lib.isFullLibrary)
        val premadesJson = File(assets, "argentum/commander-decks.json").readText()
        val mine = OfflineGameSetup.parsePremades(premadesJson).first { it.commander == "Animar, Soul of Elements" }
        val started = System.nanoTime()
        val (setup, registry) = OfflineGameSetup.build(lib, "You", mine.cards + "Not A Real Card", mine.commander, premadesJson, Random(5))
        val setupMs = (System.nanoTime() - started) / 1_000_000
        println("[offline-commander] setup ${setupMs} ms vs ${setup.aiDeckName}; notes=${setup.notes}")
        assertEquals(mine.commander, setup.you.commander)
        assertEquals(99, setup.you.deck.size)
        assertTrue(setup.notes.any { "Not A Real Card" in it })

        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(registry, setup.you, setup.ai) { inbox.put(it) }
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        var lastTurn = 0; var actions = 0; var over = false; var life = ""
        val decisionTypes = mutableSetOf<String>()
        while (actions < 600 && !over) {
            val msg = inbox.poll(90, TimeUnit.SECONDS) ?: break
            when (msg.optString("type")) {
                "gameOver" -> over = true
                "error" -> println("[offline-commander] error ${msg.optString("message")}")
                "stateUpdate" -> {
                    val snap = ArgentumTableSnapshot.parse(msg.getJSONObject("state"), msg.optJSONArray("legalActions") ?: JSONArray(), msg.optString("interactionEpoch"))
                    lastTurn = snap.turnNumber
                    life = snap.players.joinToString { "${it.name}=${it.life}" }
                    if (snap.gameOver) { over = true; continue }
                    val legal = snap.legalActions.map { it.getJSONObject("action") }
                    val chosen = legal.firstOrNull { it.optString("type") == "PlayLand" }
                        ?: legal.firstOrNull { it.optString("type") in setOf("PassPriority", "DeclareAttackers", "DeclareBlockers") }
                    if (chosen != null) { send(host, chosen); actions++; continue }
                    if (msg.isNull("pendingDecision")) continue
                    val d = msg.getJSONObject("pendingDecision")
                    decisionTypes += d.optString("type")
                    val response = when (d.optString("type")) {
                        "SelectCardsDecision" -> JSONObject().put("type", "CardsSelectedResponse").put("selectedCards",
                            JSONArray().apply { val o = d.getJSONArray("options"); for (i in 0 until d.optInt("minSelections")) put(o.getString(i)) })
                        "YesNoDecision" -> JSONObject().put("type", "YesNoResponse").put("choice", false)
                        "ChooseOptionDecision" -> JSONObject().put("type", "OptionChosenResponse").put("optionIndex", 0)
                        else -> { println("[offline-commander] unhandled decision ${d.toString().take(300)}"); null }
                    } ?: break
                    send(host, JSONObject().put("type", "SubmitDecision").put("playerId", d.getString("playerId")).put("response", response.put("decisionId", d.getString("id"))))
                    actions++
                }
            }
        }
        host.shutdown()
        println("[offline-commander] turn=$lastTurn actions=$actions over=$over life=$life decisions=$decisionTypes")
        assertTrue("commander game advanced", lastTurn >= 5 || over)
    }

    private fun send(host: LocalArgentumHost, action: JSONObject) =
        host.receive(JSONObject().put("type", "submitAction").put("action", action).put("interactionEpoch", "offline"))
}

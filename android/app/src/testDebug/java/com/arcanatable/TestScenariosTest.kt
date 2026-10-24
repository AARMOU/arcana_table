package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.OfflineCardLibrary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Debug-only test scenarios (src/debug). Each one is loaded the way the app does it, through
 * LocalArgentumHost's normal resume path, and its intended first interaction is played through
 * Arcana's own action plan. These run in testDebugUnitTest; release builds have no scenarios.
 */
class TestScenariosTest {
    private val library = OfflineCardLibrary({ path -> File("src/main/assets/$path").takeIf { it.exists() }?.inputStream() })

    private class Session(val host: LocalArgentumHost, val inbox: LinkedBlockingQueue<JSONObject>) {
        fun nextState(): JSONObject {
            repeat(40) {
                val msg = inbox.poll(30, TimeUnit.SECONDS) ?: error("Host stopped responding")
                when (msg.optString("type")) {
                    "stateUpdate" -> if ((msg.optJSONArray("legalActions")?.length() ?: 0) > 0) return msg
                    "error" -> error("Host error: ${msg.optString("message")}")
                    "mulliganDecision" -> error("A scenario must not ask for a mulligan")
                }
            }
            error("No interactive state")
        }
    }

    private fun start(scenario: TestScenario): Session {
        val game = TestScenarios.build(scenario, library)
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(game.registry, game.seats[0], game.seats[1], paceMs = 0,
            resumeCheckpoint = game.checkpoint.toString()) { inbox.put(it) }
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        return Session(host, inbox)
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    @Test fun debugBuildListsTheIronSpiderScenario() {
        assertEquals(listOf(TestScenarios.IRON_SPIDER), TestScenarios.available)
    }

    @Test fun ironSpiderScenarioOpensOnYourMainPhaseWithTheBoardAndDrawsThroughArcanasPicker() {
        val s = start(TestScenarios.IRON_SPIDER)
        try {
            val msg = s.nextState()
            val state = msg.getJSONObject("state")
            val snapshot = ArgentumTableSnapshot.parse(state, msg.getJSONArray("legalActions"), msg.optString("interactionEpoch"))
            assertEquals("your turn 1", 1, snapshot.turnNumber)
            // The draw ability, with the counter cost filled in by the host (#104) and only +1/+1 listed.
            val draw = snapshot.legalActions.single {
                it.optJSONObject("action")?.optString("type") == "ActivateAbility" &&
                    it.optJSONObject("action")?.optString("abilityId") == "Iron Spider, Stark Upgrade:2"
            }
            assertTrue("affordable with the two Islands", draw.optBoolean("isAffordable"))
            val plan = ArgentumActionPlan.from(draw)
            val removal = plan.counterRemoval!!
            assertEquals(2, removal.total)
            assertEquals("Ornithopter offers 2 (its charge counters don't count), Sol Ring 1",
                listOf(2, 1), removal.sources.values.toList())
            assertEquals(setOf("+1/+1"), removal.counterTypes.values.toSet())
            // Split 1 + 1 across the creature and the noncreature artifact, then submit as the app does.
            val split = removal.sources.keys.associateWith { 1 }
            val action = plan.build(emptyMap(), null, emptyMap(), null, split) { null }
            val handBefore = handCount(state)
            s.host.receive(JSONObject().put("type", "submitAction").put("action", action).put("interactionEpoch", msg.getString("interactionEpoch")))
            val after = s.nextState().getJSONObject("state")
            assertEquals("drew a card", handBefore + 1, handCount(after))
        } finally { s.host.shutdown() }
    }

    private fun handCount(state: JSONObject): Int {
        val zones = state.optJSONArray("zones") ?: return -1
        return zones.objects().firstOrNull {
            it.optJSONObject("zoneId")?.let { z -> z.optString("zoneType").equals("HAND", ignoreCase = true) && z.optString("ownerId") == LocalArgentumHost.HUMAN_ID.value } == true
        }?.let { it.optJSONArray("cardIds")?.length() ?: it.optInt("size", -1) } ?: -1
    }
}

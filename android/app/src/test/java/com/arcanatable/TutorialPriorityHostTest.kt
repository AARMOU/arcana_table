package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.OfflineCardLibrary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Exercises the real host protocol, casting and resolving the actual practice spell. */
class TutorialPriorityHostTest {
    private fun exercise(training: Boolean) {
        val library = OfflineCardLibrary(open = { path -> File("src/main/assets/$path").takeIf(File::exists)?.inputStream() })
        val scenario = TutorialPracticeScenarios.build(TutorialPracticeScenarios.STACK, library)
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(scenario.registry, scenario.seats[0], scenario.seats[1],
            paceMs = 0, resumeCheckpoint = scenario.checkpoint.toString(),
            tutorialPassiveOpponent = training, emit = { inbox.put(it) })
        fun nextInteractive(): JSONObject {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < deadline) {
                val message = inbox.poll(1, TimeUnit.SECONDS) ?: continue
                check(message.optString("type") != "error") { message.toString() }
                if (message.optString("type") == "stateUpdate" && message.optBoolean("interactive", true)) return message
            }
            error("Timed out waiting for actual host priority, training=$training")
        }
        fun snapshot(message: JSONObject) = ArgentumTableSnapshot.parse(message.getJSONObject("state"),
            message.optJSONArray("legalActions") ?: JSONArray(), message.getString("interactionEpoch"))
        fun send(message: JSONObject, action: JSONObject) = host.receive(JSONObject()
            .put("type", "submitAction").put("interactionEpoch", message.getString("interactionEpoch"))
            .put("action", action))
        try {
            host.receive(JSONObject().put("type", "createQuickGameLobby"))
            val initial = nextInteractive()
            val before = snapshot(initial)
            val you = before.players[before.viewingPlayer]
            val bear = you.battlefield.first { it.name == "Grizzly Bears" }
            val growth = you.hand.first { it.name == "Giant Growth" }
            val growthId = before.serverCardIds.getValue(growth.id)
            val cast = JSONObject(before.legalActions.first {
                val action = it.getJSONObject("action")
                action.optString("type") == "CastSpell" && action.optString("cardId") == growthId
            }.getJSONObject("action").toString())
            cast.put("targets", JSONArray().put(JSONObject().put("type", "Permanent")
                .put("entityId", before.serverCardIds.getValue(bear.id))))
            send(initial, cast)
            var message = nextInteractive()
            var after = snapshot(message)
            if (training) {
                assertTrue("Training must stop with the real Giant Growth on the stack", after.stack.any { it.name == "Giant Growth" })
                val pass = after.legalActions.first { it.getJSONObject("action").optString("type") == "PassPriority" }
                send(message, pass.getJSONObject("action"))
                message = nextInteractive()
                after = snapshot(message)
            }
            assertTrue("A real pass must resolve the spell; ordinary games retain auto-pass", after.stack.isEmpty())
            assertTrue("The resolved spell must reach the real graveyard", after.players[after.viewingPlayer].graveyard.any { it.name == "Giant Growth" })
            assertTrue("The target must remain on the battlefield", after.players[after.viewingPlayer].battlefield.any { it.name == "Grizzly Bears" })
        } finally {
            host.shutdown()
        }
    }

    @Test fun trainingWaitsForTheLearnersRealPriorityPass() = exercise(training = true)
    @Test fun ordinaryGameStillAutoPassesItsOwnPracticeSpell() = exercise(training = false)
}

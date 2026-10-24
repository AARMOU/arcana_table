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

    @Test fun debugBuildListsAllBuiltInScenarios() {
        assertEquals(listOf(TestScenarios.IRON_SPIDER, TestScenarios.WHALE_JUST_CAST, TestScenarios.WHALE_HELD, TestScenarios.DISPLACER_BLINK), TestScenarios.available)
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

    @Test fun displacerScenarioBlinksThroughTheAppsTargetPickerAndDrawsOnReentry() {
        val s = start(TestScenarios.DISPLACER_BLINK)
        try {
            val msg = s.nextState()
            val state = msg.getJSONObject("state")
            val source = cardIdNamed(state, "Eldrazi Displacer")
            val visionary = cardIdNamed(state, "Elvish Visionary")
            val bear = cardIdNamed(state, "Grizzly Bears")
            val snap = msg.snapshot()
            val blink = snap.legalActions.single {
                it.actionType() == "ActivateAbility" && it.getJSONObject("action").optString("sourceId") == source
            }
            assertTrue(blink.optBoolean("isAffordable"))
            val plan = ArgentumActionPlan.from(blink)
            val slot = plan.slots.single()
            val legal = plan.legalFor(slot, emptyMap())
            assertTrue(visionary in legal)
            assertTrue(bear in legal)
            assertFalse("Another creature excludes Displacer", source in legal)
            val action = plan.build(mapOf(slot.index to listOf(visionary)), null, emptyMap(), null) {
                snap.targets[it]?.let { ref -> ArgentumActionPlan.chosenTarget(ref) }
            }
            val after = s.submit(msg, action).getJSONObject("state")
            assertEquals("Reentry draws one card", handCount(state) + 1, handCount(after))
            val returned = after.getJSONObject("cards").getJSONObject(cardIdNamed(after, "Elvish Visionary"))
            assertTrue("The returned creature is tapped", returned.getBoolean("isTapped"))
            assertEquals("Counters did not survive the blink", 0, returned.optJSONObject("counters")?.optInt("PLUS_ONE_PLUS_ONE") ?: 0)
            assertEquals("Printed power restored", "1", returned.get("power").toString())
        } finally { s.host.shutdown() }
    }

    // ---- The Lunar Whale (#108): Crew 1, flying 3/5 ----

    private fun JSONObject.actionType() = optJSONObject("action")?.optString("type")

    private fun cardIdNamed(state: JSONObject, name: String): String {
        val cards = state.getJSONObject("cards")
        return cards.keys().asSequence().single { cards.getJSONObject(it).optString("name") == name }
    }

    private fun Session.submit(msg: JSONObject, action: JSONObject): JSONObject {
        host.receive(JSONObject().put("type", "submitAction").put("action", action).put("interactionEpoch", msg.getString("interactionEpoch")))
        return nextState()
    }

    private fun JSONObject.snapshot() = ArgentumTableSnapshot.parse(getJSONObject("state"), getJSONArray("legalActions"), optString("interactionEpoch"))

    /** Your interactive state offering an action of this type, passing priority (stack included) on the way. */
    private fun Session.passUntil(from: JSONObject, stopIf: (ArgentumTableSnapshot) -> Boolean = { false }, type: String): Pair<JSONObject, JSONObject?> {
        var msg = from
        repeat(30) {
            val snap = msg.snapshot()
            snap.legalActions.firstOrNull { it.actionType() == type }?.let { return msg to it }
            if (stopIf(snap)) return msg to null
            val pass = snap.legalActions.first { it.actionType() == "PassPriority" }
            msg = submit(msg, pass.getJSONObject("action"))
        }
        error("Never offered $type")
    }

    /** Crew through Arcana's own plan with its default pick; returns the state after and the crew chosen. */
    private fun Session.crewTheWhale(msg: JSONObject, whale: String): Pair<JSONObject, List<String>> {
        val (at, crew) = passUntil(msg, type = "CrewVehicle")
        assertEquals("Crew is offered on the Whale", whale, crew!!.getJSONObject("action").getString("vehicleId"))
        val plan = ArgentumActionPlan.from(crew)
        val picks = plan.crew!!.initial()
        return submit(at, plan.build(emptyMap(), null, emptyMap(), null, crewPicks = picks) { null }) to picks
    }

    @Test fun whaleJustCastIsCastForRealCrewedAndCannotAttack() {
        val s = start(TestScenarios.WHALE_JUST_CAST)
        try {
            var msg = s.nextState()
            assertEquals("your turn 1", 1, msg.snapshot().turnNumber)
            val state = msg.getJSONObject("state")
            val whaleCard = cardIdNamed(state, "The Lunar Whale")
            val elves = cardIdNamed(state, "Llanowar Elves")
            val cast = msg.snapshot().legalActions.single { it.actionType() == "CastSpell" && it.getJSONObject("action").optString("cardId") == whaleCard }
            assertTrue("four Islands pay {3}{U}", cast.optBoolean("isAffordable"))
            msg = s.submit(msg, ArgentumActionPlan.from(cast).build(emptyMap(), null, emptyMap(), null) { null })
            val (crewed, picks) = s.crewTheWhale(msg, whaleCard)
            assertEquals("the summoning-sick Elves crew it", listOf(elves), picks)
            // On to combat: the Whale entered this turn, so it is never a legal attacker.
            val (_, attack) = s.passUntil(crewed, stopIf = { it.stepKey == "POSTCOMBAT_MAIN" || it.turnNumber > 1 }, type = "DeclareAttackers")
            if (attack != null) {
                val valid = attack.optJSONArray("validAttackers")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                assertFalse("just-cast Whale can't attack", whaleCard in valid)
            }
        } finally { s.host.shutdown() }
    }

    @Test fun whaleHeldSinceLastTurnCrewsThenAttacksForThree() {
        val s = start(TestScenarios.WHALE_HELD)
        try {
            val msg = s.nextState()
            assertEquals("your turn 3", 3, msg.snapshot().turnNumber)
            val state = msg.getJSONObject("state")
            val whale = cardIdNamed(state, "The Lunar Whale")
            val elves = cardIdNamed(state, "Llanowar Elves")
            val (crewed, picks) = s.crewTheWhale(msg, whale)
            assertEquals(listOf(elves), picks)
            val (at, attack) = s.passUntil(crewed, type = "DeclareAttackers")
            val plan = ArgentumAttackPlan.from(attack!!)!!
            assertTrue("held, crewed Whale is a legal attacker", plan.options.any { it.attackerId == whale })
            val ai = msg.snapshot().players.indices.first { it != msg.snapshot().viewingPlayer }
            val lifeBefore = msg.snapshot().players[ai].life
            var after = s.submit(at, plan.buildAction(mapOf(whale to plan.options.first { it.attackerId == whale }.targets.first())))
            repeat(10) {
                if (after.snapshot().players[ai].life < lifeBefore) return@repeat
                val pass = after.snapshot().legalActions.firstOrNull { it.actionType() == "PassPriority" } ?: return@repeat
                after = s.submit(after, pass.getJSONObject("action"))
            }
            assertEquals("3 damage in the air", lifeBefore - 3, after.snapshot().players[ai].life)
        } finally { s.host.shutdown() }
    }

    private fun handCount(state: JSONObject): Int {
        val zones = state.optJSONArray("zones") ?: return -1
        return zones.objects().firstOrNull {
            it.optJSONObject("zoneId")?.let { z -> z.optString("zoneType").equals("HAND", ignoreCase = true) && z.optString("ownerId") == LocalArgentumHost.HUMAN_ID.value } == true
        }?.let { it.optJSONArray("cardIds")?.length() ?: it.optInt("size", -1) } ?: -1
    }
}

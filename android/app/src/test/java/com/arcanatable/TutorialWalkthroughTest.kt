package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.OfflineCardLibrary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Plays every engine-backed practice lesson from its first step to its last, the way a learner
 * following the coach would: tap what it says to tap, choose the Argentum action it describes,
 * pass when it says pass. The real offline host runs the rules, and the lesson moves on only
 * through [TutorialRun] and [TutorialEventDetector], the same code the app uses. A step that
 * can't be completed fails with a trace of the game and the actions Argentum offered.
 */
class TutorialWalkthroughTest {
    private val lessons = parseTutorialLessons(File("src/main/assets/tutorials/catalog.json").readText())
    private val library = OfflineCardLibrary(open = { path -> File("src/main/assets/$path").takeIf(File::exists)?.inputStream() })

    private fun lesson(id: String) = lessons.first { it.id == id }

    /** A learner at the table: the host's messages in, actions out, the lesson watching. */
    private inner class Table(val run: TutorialRun, makeHost: ((JSONObject) -> Unit) -> LocalArgentumHost) {
        private val inbox = LinkedBlockingQueue<JSONObject>()
        val host = makeHost { inbox.put(it) }
        val detector = TutorialEventDetector()
        var mulliganPending = false
        var snapshot: ArgentumTableSnapshot? = null
        var message: JSONObject? = null
        var mulligan: JSONObject? = null
        var bottom: JSONObject? = null
        val trace = mutableListOf<String>()

        /** Reads messages until Argentum waits on the learner again. */
        fun settle(timeoutSeconds: Long = 30) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (System.nanoTime() < deadline) {
                val m = inbox.poll(if (waiting()) 300 else 1000, TimeUnit.MILLISECONDS) ?: if (waiting()) return else continue
                when (m.optString("type")) {
                    "error" -> fail("Argentum error during ${run.step?.id}: $m\n${trace.joinToString("\n")}")
                    "mulliganDecision" -> {
                        mulligan = m
                        if (mulliganPending) { mulliganPending = false; signal("event", "HandMulliganed") }
                    }
                    "chooseBottomCards" -> bottom = m
                    "stateUpdate" -> {
                        mulligan = null; bottom = null
                        val next = ArgentumTableSnapshot.parse(m.getJSONObject("state"), m.optJSONArray("legalActions") ?: JSONArray(), m.optString("interactionEpoch"))
                        val previous = snapshot
                        snapshot = next; message = m
                        trace += "  state ${next.stepKey} turn ${next.turnNumber} active=${next.activePlayer} stack=${next.stack.map { it.name }} legal=${next.legalActions.map { it.getJSONObject("action").optString("type") }.distinct()}"
                        detector.detect(previous, next, { run.step }) { signal("event", it) }
                    }
                }
            }
            if (!waiting()) fail("Timed out waiting for Argentum during ${run.step?.id}\n${trace.joinToString("\n")}")
        }

        private fun waiting(): Boolean = mulligan != null || bottom != null ||
            (message?.optBoolean("interactive", true) == true && inbox.isEmpty())

        fun signal(kind: String, key: String) {
            val before = run.step?.id
            if (run.signal(kind, key)) trace += "    $kind $key: $before -> ${run.step?.id ?: "DONE"}"
        }

        fun legal(): List<JSONObject> = snapshot?.legalActions.orEmpty()
        fun actionOf(type: String, predicate: (JSONObject) -> Boolean = { true }) =
            legal().firstOrNull { it.getJSONObject("action").optString("type") == type && predicate(it.getJSONObject("action")) }

        fun serverId(card: GameCard) = snapshot!!.serverCardIds.getValue(card.id)
        val you get() = snapshot!!.players[snapshot!!.viewingPlayer]

        /** Sends a legal action the way the app does (PlayCardDecision wrapping included). */
        fun submit(action: JSONObject) {
            val m = message!!
            val decision = m.optJSONObject("pendingDecision")
            val type = action.optString("type")
            val outbound = if (decision?.optString("type") == "PlayCardDecision" && type in setOf("CastSpell", "PlayLand"))
                JSONObject().put("type", "SubmitDecision").put("playerId", decision.optString("playerId"))
                    .put("response", JSONObject().put("type", "PlayCardResponse").put("decisionId", decision.optString("id")).put("action", JSONObject(action.toString())))
            else action
            trace += "  learner: $type during ${run.step?.id}"
            if (type == "PassPriority") detector.passPending = true
            message = null // wait for the reply
            host.receive(JSONObject().put("type", "submitAction").put("interactionEpoch", m.getString("interactionEpoch")).put("action", outbound))
            settle()
        }

        fun pass() {
            val pass = actionOf("PassPriority") ?: return declareAttackers(all = false)
            submit(pass.getJSONObject("action"))
        }

        fun declareAttackers(all: Boolean) {
            val info = legal().firstOrNull { it.getJSONObject("action").optString("type") == "DeclareAttackers" }
                ?: fail("No PassPriority or DeclareAttackers to continue during ${run.step?.id}\n${trace.joinToString("\n")}")
            val plan = ArgentumAttackPlan.from(info)!!
            submit(plan.buildAction(if (all) plan.options.associate { it.attackerId to it.targets.first() } else emptyMap()))
        }

        fun send(type: String, extra: JSONObject = JSONObject()) {
            trace += "  learner: $type during ${run.step?.id}"
            mulligan = null; bottom = null; message = null
            host.receive(JSONObject(extra.toString()).put("type", type))
            settle()
        }
    }

    /** Plays [lessonId] to the end, failing on the first step a learner can't complete. */
    private fun walk(lessonId: String, makeHost: ((JSONObject) -> Unit) -> LocalArgentumHost, attack: Boolean) {
        val table = Table(TutorialRun(lesson(lessonId), practice = true), makeHost)
        try {
            table.host.receive(JSONObject().put("type", "createQuickGameLobby"))
            table.settle()
            var actions = 0
            while (!table.run.finished) {
                check(actions++ < 120) { "Lesson $lessonId never finished\n${table.trace.joinToString("\n")}" }
                val step = table.run.step!!
                val before = step.id
                table.trace += "STEP ${table.run.index + 1} ${step.id} (${step.actionKind}${step.event?.let { ":$it" }.orEmpty()})"
                when (step.actionKind) {
                    "next" -> table.run.next()
                    "tap" -> table.signal("tap", step.target ?: "")
                    "hold" -> {
                        val target = step.target ?: ""
                        val zone = target.substringBefore(':')
                        val any = table.you.let { if (zone == "hand-card") it.hand else it.battlefield }.firstOrNull()?.name
                        table.signal("hold", if (target.endsWith(":any") && any != null) "$zone:$any" else target)
                    }
                    "event" -> learnerActsFor(table, step, attack)
                }
                if (!table.run.finished && table.run.step?.id == before && step.actionKind != "next") {
                    // One more natural action (e.g. a second pass) before declaring the step stuck.
                    learnerActsFor(table, step, attack)
                    if (!table.run.finished && table.run.step?.id == before)
                        fail("Lesson $lessonId is stuck at step ${table.run.index + 1} \"${step.title}\" (${step.id}).\n${table.trace.joinToString("\n")}")
                }
            }
            assertTrue(table.run.finished)
            println("Lesson $lessonId completed:\n${table.trace.joinToString("\n")}")
        } finally {
            table.host.shutdown()
        }
    }

    /** What the coach text asks the learner to do for an event step. */
    private fun learnerActsFor(table: Table, step: ArcanaLessonStep, attack: Boolean) {
        when (step.event) {
            "LandPlayed" -> {
                val land = table.you.hand.first { "land" in it.type.lowercase() }
                val play = table.actionOf("PlayLand") { it.optString("cardId") == table.serverId(land) }
                    ?: return fail("No PlayLand for ${land.name}; offered ${table.legal().map { it.getJSONObject("action") }}")
                table.submit(play.getJSONObject("action"))
            }
            "ManaAdded" -> {
                val lands = table.you.battlefield.filter { "land" in it.type.lowercase() && !it.tapped }.map(table::serverId).toSet()
                val ability = table.legal().firstOrNull { info ->
                    val a = info.getJSONObject("action")
                    a.optString("type").contains("Activate") && (a.optString("sourceId") in lands || a.optString("cardId") in lands)
                } ?: return fail("Step \"${step.title}\": Argentum offers no mana ability for an untapped land. Offered: " +
                    table.legal().map { it.getJSONObject("action").optString("type") } + "\n${table.trace.joinToString("\n")}")
                table.submit(ability.getJSONObject("action"))
            }
            "SpellCast" -> {
                val name = step.target?.removePrefix("hand-card:")
                val card = table.you.hand.first { it.name == name }
                val cast = table.actionOf("CastSpell") { it.optString("cardId") == table.serverId(card) }
                    ?: return fail("No CastSpell for $name during ${step.id}; offered ${table.legal().map { it.getJSONObject("action").optString("type") }}\n${table.trace.joinToString("\n")}")
                val action = JSONObject(cast.getJSONObject("action").toString())
                if (cast.optBoolean("requiresTargets")) {
                    val creature = table.you.battlefield.first { "creature" in it.type.lowercase() }
                    action.put("targets", JSONArray().put(JSONObject().put("type", "Permanent").put("entityId", table.serverId(creature))))
                }
                table.submit(action)
            }
            "AttackersDeclared" -> if (table.actionOf("DeclareAttackers") != null) table.declareAttackers(all = attack) else table.pass()
            "HandMulliganed" -> { table.mulliganPending = true; table.send("mulligan") }
            "HandKept" -> {
                table.detector.keepPending = true
                table.send("keepHand")
                table.bottom?.let { b ->
                    val hand = b.getJSONArray("hand")
                    val ids = (0 until b.optInt("cardsToPutOnBottom")).map { hand.getString(it) }
                    table.send("chooseBottomCards", JSONObject().put("cardIds", JSONArray(ids)))
                }
            }
            else -> table.pass() // SpellResolved, PriorityPassed, TurnAdvanced, CombatDamageDealt
        }
    }

    private fun checkpointHost(id: String): ((JSONObject) -> Unit) -> LocalArgentumHost {
        val scenario = TutorialPracticeScenarios.build(id, library)
        return { emit -> LocalArgentumHost(scenario.registry, scenario.seats[0], scenario.seats[1], paceMs = 0,
            resumeCheckpoint = scenario.checkpoint.toString(), tutorialPassiveOpponent = true, emit = emit) }
    }

    @Test fun firstTurnLessonCanBeCompleted() = walk(TutorialPracticeScenarios.FIRST_TURN, checkpointHost(TutorialPracticeScenarios.FIRST_TURN), attack = false)

    @Test fun combatLessonCanBeCompleted() = walk(TutorialPracticeScenarios.COMBAT, checkpointHost(TutorialPracticeScenarios.COMBAT), attack = true)

    @Test fun stackLessonCanBeCompleted() = walk(TutorialPracticeScenarios.STACK, checkpointHost(TutorialPracticeScenarios.STACK), attack = false)

    @Test fun openingHandLessonCanBeCompleted() {
        val (seats, registry) = TutorialPracticeScenarios.openingHand(library)
        walk(TutorialPracticeScenarios.MULLIGAN, { emit ->
            LocalArgentumHost(registry, seats[0], seats[1], paceMs = 0, gameSeed = 12025L, tutorialPassiveOpponent = true, emit = emit)
        }, attack = false)
    }
}

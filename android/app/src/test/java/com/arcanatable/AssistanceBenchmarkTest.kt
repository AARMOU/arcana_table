package com.arcanatable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline corpus checks for event detection, target hints, and safe fixed effects. */
class AssistanceBenchmarkTest {
    private val corpus: JSONObject by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/assistance-oracle-benchmark.json")) {
            "Missing assistance Oracle benchmark resource"
        }
        JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @Test
    fun recognizesSelfReferencingEtbsAcrossOneThousandRealCards() {
        val resource = requireNotNull(javaClass.getResourceAsStream("/etb-card-benchmark.json")) {
            "Missing 1,000-card ETB benchmark resource"
        }
        val data = JSONObject(resource.bufferedReader().use { it.readText() })
        val rows = data.getJSONArray("cards").objects()
        assertEquals("Benchmark must retain the agreed 1,000-card size", 1_000, rows.size)
        assertEquals("Benchmark should use distinct Oracle IDs", 1_000, rows.map { it.getString("oracleId") }.toSet().size)
        val misses = mutableListOf<String>()
        val recognizedByEvent = mutableMapOf<String, Int>()
        rows.forEachIndexed { index, row ->
            val cardName = row.getString("card")
            val typeLine = row.getString("typeLine")
            val expectedEvent = row.getString("event")
            val triggerText = "${row.getString("condition")}, draw a card."
            val actual = parseRecognizedTriggeredAbilities(triggerText, cardName, typeLine).singleOrNull()
            val ability = actual
            if (ability == null || ability.event != expectedEvent) {
                misses += "$cardName: expected $expectedEvent, got ${ability?.event ?: "manual"}"
                return@forEachIndexed
            }
            val event = AssistanceEvent(
                kind = expectedEvent,
                actorIndex = 0,
                subjectType = typeLine,
                subjectName = cardName,
                sourceCardId = index,
                subjectCardId = index
            )
            if (!matchesAssistanceEvent(ability, event, 0)) {
                misses += "$cardName: parsed trigger did not match its own entry event"
                return@forEachIndexed
            }
            recognizedByEvent[expectedEvent] = (recognizedByEvent[expectedEvent] ?: 0) + 1
        }
        assertTrue("Self-referencing ETB misses: ${misses.take(20).joinToString("; ")}", misses.isEmpty())
        println("ASSISTANCE_BENCHMARK|bucket=self_etb|recognized=${rows.size - misses.size}|total=${rows.size}|by_event=${recognizedByEvent.entries.joinToString(",") { "${it.key}:${it.value}" }}")
    }

    @Test
    fun reportsOracleTriggerEventCoverage() {
        val rows = corpus.getJSONArray("triggers").objects()
        val misses = mutableListOf<String>()
        var recognized = 0
        rows.forEach { row ->
            val card = row.getString("card")
            val text = row.getString("text")
            val expectedEvent = row.getString("event")
            val expectedRecognized = row.getBoolean("recognized")
            val actual = parseRecognizedTriggeredAbilities(text).firstOrNull()?.event
            if (expectedRecognized) {
                assertEquals("$card should identify its trigger event", expectedEvent, actual)
                recognized++
            } else {
                assertNull("$card should remain outside the automatic event model", actual)
                misses += "$card (${row.optString("manualReason")})"
            }

            if (row.has("match")) {
                val match = row.getJSONObject("match")
                val event = AssistanceEvent(
                    kind = match.getString("kind"),
                    actorIndex = match.getInt("actorIndex"),
                    subjectType = match.getString("subjectType"),
                    subjectName = match.optString("subjectName").takeIf { it.isNotBlank() },
                    amount = match.takeIf { it.has("amount") }?.getInt("amount"),
                    sourceCardId = match.getInt("sourceCardId"),
                    subjectCardId = match.getInt("subjectCardId")
                )
                assertTrue(
                    "$card should match the recorded controller, type, and power filters",
                    matchesAssistanceEvent(parseRecognizedTriggeredAbilities(text).single(), event, match.getInt("controllerIndex"))
                )
                if (match.has("overLimitAmount")) {
                    val outsideFilter = event.copy(amount = match.getInt("overLimitAmount"))
                    assertTrue(
                        "$card should not trigger outside its printed power condition",
                        !matchesAssistanceEvent(parseRecognizedTriggeredAbilities(text).single(), outsideFilter, match.getInt("controllerIndex"))
                    )
                }
            }
        }
        println("ASSISTANCE_BENCHMARK|bucket=trigger_events|recognized=$recognized|total=${rows.size}|misses=${misses.joinToString("; ").ifBlank { "none" }}")
    }

    @Test
    fun reportsTargetCategoryHintCoverage() {
        val rows = corpus.getJSONArray("targets").objects()
        val misses = mutableListOf<String>()
        var matched = 0
        rows.forEach { row ->
            val card = row.getString("card")
            val text = row.getString("text")
            val expected = row.getJSONArray("categories").strings().toSet()
            val actual = parseTargetCategoryHints(text)?.categories.orEmpty()
            assertEquals("$card target-category hint", expected, actual)
            if (expected.isEmpty()) misses += card else matched++
        }
        println("ASSISTANCE_BENCHMARK|bucket=target_hints|matched=$matched|total=${rows.size}|no_target_hint=${misses.joinToString(", ").ifBlank { "none" }}|legality=not_evaluated")
    }

    @Test
    fun reportsFixedEffectAutomationAndManualFallback() {
        val rows = corpus.getJSONArray("effects").objects()
        val automaticMisses = mutableListOf<String>()
        val manualFailures = mutableListOf<String>()
        var automatic = 0
        var correctlyManual = 0
        rows.forEach { row ->
            val card = row.getString("card")
            val result = parseAutomaticResolutionEffects(row.getString("text"))
            val actualAutomatic = !result.requiresManualReview && !result.isEmpty
            if (row.getBoolean("automatic")) {
                assertTrue("$card's fixed effect should be recognized", actualAutomatic)
                automatic++
            } else {
                assertTrue("$card's unsupported or choice-dependent effect must stay manual", result.requiresManualReview)
                if (result.requiresManualReview) correctlyManual++ else manualFailures += card
            }
            if (row.getBoolean("automatic") && !actualAutomatic) automaticMisses += card
        }
        println("ASSISTANCE_BENCHMARK|bucket=fixed_effects|automatic=$automatic|total=${rows.size}|manual_fallback=$correctlyManual|unsafe_or_unexpected=${(automaticMisses + manualFailures).joinToString(", ").ifBlank { "none" }}")
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
}

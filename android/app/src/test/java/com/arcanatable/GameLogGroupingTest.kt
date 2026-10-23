package com.arcanatable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLogGroupingTest {
    private fun event(id: Int, target: String = "you", text: String = "Haliya triggered") = JSONObject()
        .put("eventId", id).put("timestampMs", id * 1000L)
        .put("type", "triggeredAbility").put("description", text)
        .put("controllerId", "opponent").put("targetId", target)
        .put("lifeDelta", 1)

    private fun rowsOf(events: JSONArray, viewerId: String = "you"): List<CollapsedGameLogRow> {
        val parsed = ArgentumLogLine.parseAll(events, viewerId)
        return collapsedGameLogRows(parsed.map { it.description }, parsed.map { it.isYours },
            parsed.map { it.isTurnMarker }, parsed.map { it.groupingKey })
    }

    @Test fun exactConsecutiveEventsCollapseButOriginalLogRemainsIntact() {
        val events = JSONArray()
        repeat(1000) { events.put(event(it)) }
        val original = ArgentumLogLine.parseAll(events, "you")
        val rows = rowsOf(events)
        assertEquals(1000, original.size)
        assertEquals(1, rows.size)
        assertEquals(1000, rows.single().count)
        assertEquals(0, rows.single().firstIndex)
    }

    @Test fun aDifferentTargetOrOutcomeInterruptsAGroup() {
        val e = JSONArray()
            .put(event(1))
            .put(event(2))
            .put(event(3, target = "their-creature"))
            .put(event(4))
            .put(event(5, text = "Haliya gained two life"))
        val rows = rowsOf(e)
        assertEquals(listOf(2, 1, 1, 1), rows.map { it.count })
        assertEquals(listOf(0, 2, 3, 4), rows.map { it.firstIndex })
    }

    @Test fun turnMarkersAlwaysBreakGrouping() {
        val e = JSONArray()
            .put(event(1))
            .put(JSONObject().put("type", "turnChanged").put("description", "--- Turn 4 ---"))
            .put(event(2))
            .put(event(3))
        val rows = rowsOf(e)
        assertEquals(listOf(1, 1, 2), rows.map { it.count })
        assertTrue(rows[1].isTurnMarker)
    }

    @Test fun manualSandboxLogsDoNotCollapseWithoutStructuredProvenance() {
        val rows = collapsedGameLogRows(
            listOf("gained 1 life", "gained 1 life", "gained 1 life"),
            null, null, null
        )
        assertEquals(listOf(1, 1, 1), rows.map { it.count })
    }

    @Test fun differentControllersAndHiddenInformationDoNotConflate() {
        val e = JSONArray()
            .put(event(1).put("controllerId", "me").put("isYours", true))
            .put(event(2).put("controllerId", "opp").put("isYours", false))
        assertEquals(2, rowsOf(e).size)
    }
}

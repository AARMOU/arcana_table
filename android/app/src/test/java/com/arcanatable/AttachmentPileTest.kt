package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Equipment and Auras sit with the permanent they're attached to. */
class AttachmentPileTest {
    private fun card(id: Int, name: String, on: Int? = null) = GameCard(id, name, "Creature", Color.Gray, attachedTo = on)

    @Test fun attachmentsLeaveTheBoardAndJoinTheirHost() {
        val bear = card(1, "Grizzly Bears")
        val greaves = card(2, "Lightning Greaves", on = 1)
        val aura = card(3, "Rancor", on = 1)
        val elf = card(4, "Llanowar Elves")
        val (standalone, attached) = splitAttachments(listOf(bear, greaves, elf, aura))
        assertEquals(listOf(bear, elf), standalone)
        assertEquals(mapOf(1 to listOf(greaves, aura)), attached)
    }

    @Test fun attachmentWhoseHostIsElsewhereStaysOnItsOwn() {
        val curse = card(5, "Pacifism", on = 99)
        val (standalone, attached) = splitAttachments(listOf(curse))
        assertEquals(listOf(curse), standalone)
        assertEquals(emptyMap<Int, List<GameCard>>(), attached)
    }

    @Test fun auraOnEquipmentFollowsTheChainToThePermanent() {
        val knight = card(1, "Knight")
        val sword = card(2, "Sword", on = 1)
        val aura = card(3, "Aura on the sword", on = 2)
        val (standalone, attached) = splitAttachments(listOf(knight, sword, aura))
        assertEquals(listOf(knight), standalone)
        assertEquals(listOf(sword, aura), attached[1])
    }

    @Test fun aHostWithAttachmentsNeverMergesIntoACountStack() {
        val a = card(1, "Soldier"); val b = card(2, "Soldier")
        assertEquals(1, stackIdentical(listOf(a, b)).size)
        assertEquals(2, stackIdentical(listOf(a, b), keepApart = setOf(1)).size)
    }

    @Test fun argentumSnapshotCarriesAttachedTo() {
        val cards = JSONObject()
            .put("c1", JSONObject().put("name", "Grizzly Bears").put("typeLine", "Creature — Bear").put("zone", JSONObject().put("zoneType", "BATTLEFIELD").put("ownerId", "p1")).put("controllerId", "p1"))
            .put("c2", JSONObject().put("name", "Lightning Greaves").put("typeLine", "Artifact — Equipment").put("attachedTo", "c1").put("zone", JSONObject().put("zoneType", "BATTLEFIELD").put("ownerId", "p1")).put("controllerId", "p1"))
        val state = JSONObject()
            .put("players", JSONArray().put(JSONObject().put("playerId", "p1").put("name", "You")))
            .put("cards", cards)
            .put("zones", JSONArray().put(JSONObject().put("zoneId", JSONObject().put("zoneType", "BATTLEFIELD").put("ownerId", "p1")).put("cardIds", JSONArray().put("c1").put("c2"))))
        val battlefield = ArgentumTableSnapshot.parse(state).players.first().battlefield
        val bear = battlefield.first { it.name == "Grizzly Bears" }
        val greaves = battlefield.first { it.name == "Lightning Greaves" }
        assertNull(bear.attachedTo)
        assertEquals(bear.id, greaves.attachedTo)
    }
}

package com.arcanatable

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The client must NEVER invent casting permission from a card name, text or zone. */
class GraveyardExileLegalActionsTest {
    private fun action(type: String, key: String, id: String) = JSONObject()
        .put("description", type)
        .put("action", JSONObject().put("type", type).put(key, id))

    @Test fun engineAuthorizedGraveyardAndExileCardActionsAreSelectable() {
        val actions = listOf(
            action("CastSpell", "cardId", "graveyard-escape"),
            action("CastSpell", "cardId", "exiled-adventure"),
            action("PlayLand", "cardId", "graveyard-land"),
            action("ActivateAbility", "sourceId", "graveyard-activated"),
            action("ActivateAbility", "permanentId", "exiled-activated"),
            action("CastSpell", "entityId", "other-source"),
            action("PassPriority", "playerId", "me")
        )
        for (id in listOf("graveyard-escape", "exiled-adventure", "graveyard-land", "graveyard-activated", "exiled-activated", "other-source")) {
            assertEquals(id, 1, ArgentumActions.forCardServerId(actions, id).size)
        }
        assertTrue(ArgentumActions.forCardServerId(actions, "no-permission").isEmpty())
        assertTrue(ArgentumActions.forCardServerId(actions, null).isEmpty())
        assertTrue(ArgentumActions.forCardServerId(actions, "").isEmpty())
    }

    @Test fun differentInstancesAndNonActionPayloadsNeverBecomePermission() {
        val actions = listOf(
            action("CastSpell", "cardId", "card-2"),
            JSONObject().put("description", "Cast from exile (no action object)"),
            JSONObject().put("description", "Wrong name").put("action", JSONObject().put("type", "CastSpell"))
        )
        assertTrue(ArgentumActions.forCardServerId(actions, "card-1").isEmpty())
        assertEquals(1, ArgentumActions.forCardServerId(actions, "card-2").size)
    }
}

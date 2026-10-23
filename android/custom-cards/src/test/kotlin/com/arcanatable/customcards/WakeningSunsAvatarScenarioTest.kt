package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WakeningSunsAvatar
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeningSunsAvatarScenarioTest {
    @Test
    fun castFromHandDestroysNonDinosaursAndKeepsDinosaurs() {
        val d = customScenario(WakeningSunsAvatar, "Llanowar Elves")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Llanowar Elves")
        d.putCreatureOnBattlefield(player, "Compy Swarm")
        val avatar = d.putCardInHand(player, "Wakening Sun's Avatar")
        d.giveMana(player, Color.WHITE, 8)

        assertNull(d.castSpell(player, avatar).error)
        d.resolveAll()

        val creatureNames = d.getCreatures(player).map(d::getCardName).toSet()
        assertEquals(setOf("Compy Swarm", "Wakening Sun's Avatar"), creatureNames)
    }
}

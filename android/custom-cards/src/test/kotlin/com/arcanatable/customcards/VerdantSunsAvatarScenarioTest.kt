package com.arcanatable.customcards

import com.arcanatable.customcards.cards.VerdantSunsAvatar
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class VerdantSunsAvatarScenarioTest {
    @Test
    fun avatarAndOtherControlledCreatureEntryGainThatCreaturesToughness() {
        val d = customScenario(VerdantSunsAvatar, "Grizzly Bears")
        val player = d.player1

        val avatar = d.putCardInHand(player, "Verdant Sun's Avatar")
        d.giveMana(player, Color.GREEN, 2)
        d.giveColorlessMana(player, 5)
        assertNull(d.castSpell(player, avatar).error)
        d.resolveAll()
        assertEquals(25, d.getLifeTotal(player))

        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, bears).error)
        d.resolveAll()
        assertEquals(27, d.getLifeTotal(player))
    }

    @Test
    fun opponentCreatureEntryDoesNotGainLife() {
        val d = customScenario(VerdantSunsAvatar, "Grizzly Bears")
        val player = d.player1

        val avatar = d.putCardInHand(player, "Verdant Sun's Avatar")
        d.giveMana(player, Color.GREEN, 2)
        d.giveColorlessMana(player, 5)
        assertNull(d.castSpell(player, avatar).error)
        d.resolveAll()
        assertEquals(25, d.getLifeTotal(player))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val bears = d.putCardInHand(d.player2, "Grizzly Bears")
        d.giveMana(d.player2, Color.GREEN)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.castSpell(d.player2, bears).error)
        d.resolveAll()
        assertEquals(25, d.getLifeTotal(player))
    }
}

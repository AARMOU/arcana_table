package com.arcanatable.customcards

import com.arcanatable.customcards.cards.VerdantSunsAvatar
import com.arcanatable.customcards.support.GameTestDriver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class VerdantSunsAvatarScenarioTest {
    @Test
    fun avatarAndControlledCreatureEnteringGainTheirToughnessInLife() {
        val d = customScenario(VerdantSunsAvatar, "Grizzly Bears")
        val p = d.player1
        castAvatar(d, p)
        assertEquals(25, d.getLifeTotal(p))

        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()

        assertEquals(27, d.getLifeTotal(p))
    }

    @Test
    fun opposingCreatureEntryDoesNotTriggerLifeGain() {
        val d = customScenario(VerdantSunsAvatar, "Grizzly Bears")
        val p = d.player1
        castAvatar(d, p)
        assertEquals(25, d.getLifeTotal(p))

        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val bear = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, bear).error)
        d.resolveAll()

        assertEquals(25, d.getLifeTotal(p))
    }

    private fun castAvatar(d: GameTestDriver, player: EntityId) {
        val avatar = d.putCardInHand(player, "Verdant Sun's Avatar")
        d.giveMana(player, Color.GREEN)
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 5)
        assertNull(d.castSpell(player, avatar).error)
        d.resolveAll()
    }
}

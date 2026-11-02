package com.arcanatable.customcards

import com.arcanatable.customcards.cards.VerdantSunsAvatar
import com.arcanatable.customcards.support.GameTestDriver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
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

        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.resolveAll()

        assertEquals(27, d.getLifeTotal(p))
    }

    @Test
    fun opposingCreatureEntryDoesNotTriggerLifeGain() {
        val d = customScenario(VerdantSunsAvatar, "Grizzly Bears")
        val p = d.player1
        castAvatar(d, p)
        assertEquals(25, d.getLifeTotal(p))

        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
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

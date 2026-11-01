package com.arcanatable.customcards

// Independent trusted-main Oracle checks for Verdant Sun's Avatar (#462).
// This is deliberately separate from the Copilot-authored card implementation and scenarios.
// Real Argentum game actions must establish both positive and negative ETB behavior.
import com.arcanatable.customcards.cards.VerdantSunsAvatar
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class VerdantSunsAvatarIndependentCertificationTest {
    private fun GameTestDriver.castAvatar(player: EntityId) {
        val card = putCardInHand(player, "Verdant Sun's Avatar")
        giveMana(player, Color.GREEN, 2)
        giveColorlessMana(player, 5)
        assertNull(castSpell(player, card).error)
        resolveAll()
    }

    @Test
    fun ownEntryTriggersExactlyOnceForFiveLife() {
        val d = customScenario(VerdantSunsAvatar)
        val p = d.player1
        assertEquals(20, d.getLifeTotal(p))
        d.castAvatar(p)
        assertEquals(25, d.getLifeTotal(p))
        assertEquals(1, d.permanentsNamed(p, "Verdant Sun's Avatar").size)
    }

    @Test
    fun anotherControlledCreatureUsesToughnessRatherThanPower() {
        val d = customScenario(VerdantSunsAvatar, "Giant Spider")
        val p = d.player1
        d.castAvatar(p)
        val spider = d.putCardInHand(p, "Giant Spider") // 2/4: gain four, not two.
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, spider).error)
        d.resolveAll()
        assertEquals(29, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun opponentsCreatureEnteringDoesNotTriggerOurAvatar() {
        val d = customScenario(VerdantSunsAvatar, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.castAvatar(p)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val bear = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, bear).error)
        d.resolveAll()
        assertEquals(25, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(opponent))
    }

    @Test
    fun removedAvatarCannotGainLifeFromLaterCreatureEntry() {
        val d = customScenario(VerdantSunsAvatar, "Murder", "Grizzly Bears")
        val p = d.player1
        d.castAvatar(p)
        val avatar = d.findPermanent(p, "Verdant Sun's Avatar")
        assertNotNull(avatar)
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpellWithTargets(p, murder, listOf(ChosenTarget.Permanent(avatar!!))).error)
        d.resolveAll()
        assertNull(d.findPermanent(p, "Verdant Sun's Avatar"))
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        assertEquals(25, d.getLifeTotal(p))
    }

    @Test
    fun twoAvatarsEachTriggerForSecondAvatarsEntry() {
        val d = customScenario(VerdantSunsAvatar)
        val p = d.player1
        d.castAvatar(p)
        assertEquals(25, d.getLifeTotal(p))
        d.castAvatar(p)
        assertEquals(35, d.getLifeTotal(p))
        assertEquals(2, d.permanentsNamed(p, "Verdant Sun's Avatar").size)
    }
}

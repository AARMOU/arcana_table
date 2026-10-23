package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SisterOfSilence
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SisterOfSilenceScenarioTest {
    private fun flashSister(d: GameTestDriver) {
        d.passPriority(d.player2)
        val sister = d.putCardInHand(d.player1, "Sister of Silence")
        d.giveColorlessMana(d.player1, 4)
        d.giveMana(d.player1, Color.BLUE)
        assertNull(d.castSpell(d.player1, sister).error)
    }

    @Test
    fun flashEntryCountersAnInstantBeforeItDraws() {
        val d = customScenario(SisterOfSilence, "Opt")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val opt = d.putCardInHand(d.player2, "Opt")
        d.giveMana(d.player2, Color.BLUE)
        assertNull(d.castSpell(d.player2, opt).error)
        val spell = d.getTopOfStack()!!
        val before = d.getHand(d.player2).size
        flashSister(d)
        d.resolveAll(Choices(targets = listOf(spell)))
        assertEquals(before, d.getHand(d.player2).size)
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Opt"))
        val sister = d.findPermanent(d.player1, "Sister of Silence")!!
        assertTrue(d.projected.hasKeyword(sister, Keyword.FLASH))
    }

    @Test
    fun entryCountersASorceryBeforeItDraws() {
        val d = customScenario(SisterOfSilence, "Divination")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val divination = d.putCardInHand(d.player2, "Divination")
        d.giveMana(d.player2, Color.BLUE)
        d.giveColorlessMana(d.player2, 2)
        assertNull(d.castSpell(d.player2, divination).error)
        val spell = d.getTopOfStack()!!
        val before = d.getHand(d.player2).size
        flashSister(d)
        d.resolveAll(Choices(targets = listOf(spell)))
        assertEquals(before, d.getHand(d.player2).size)
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Divination"))
    }

    @Test
    fun entryCountersActivatedAbilityWhileSourceRemainsTappedOnBattlefield() {
        val d = customScenario(SisterOfSilence, "Prodigal Sorcerer")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val source = d.putCreatureOnBattlefield(d.player2, "Prodigal Sorcerer")
        d.removeSummoningSickness(source)
        val ability = d.cardRegistry.getCard("Prodigal Sorcerer")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(d.player2, source, ability.id,
            targets = listOf(ChosenTarget.Player(d.player1)))).error)
        val onStack = d.getTopOfStack()!!
        flashSister(d)
        d.resolveAll(Choices(targets = listOf(onStack)))
        assertEquals(20, d.getLifeTotal(d.player1))
        assertEquals(source, d.findPermanent(d.player2, "Prodigal Sorcerer"))
        assertTrue(d.isTapped(source))
    }

    @Test
    fun entryCountersTriggeredLifeGainWithoutRemovingItsSource() {
        val d = customScenario(SisterOfSilence, "Soul Warden", "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.putCreatureOnBattlefield(d.player2, "Soul Warden")
        val bear = d.putCardInHand(d.player2, "Grizzly Bears")
        d.giveMana(d.player2, Color.GREEN)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.castSpell(d.player2, bear).error)
        d.bothPass()
        val lifeGain = d.getTopOfStack()!!
        flashSister(d)
        // Sister's entry also triggers Soul Warden; counter the older bear-entry trigger.
        d.resolveAll(Choices(targets = listOf(lifeGain)))
        assertEquals(21, d.getLifeTotal(d.player2))
        assertNotNull(d.findPermanent(d.player2, "Soul Warden"))
    }

    @Test
    fun entryCannotCounterACreatureSpell() {
        val d = customScenario(SisterOfSilence, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val bear = d.putCardInHand(d.player2, "Grizzly Bears")
        d.giveMana(d.player2, Color.GREEN)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.castSpell(d.player2, bear).error)
        flashSister(d)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player2, "Grizzly Bears"))
        assertFalse(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }
}

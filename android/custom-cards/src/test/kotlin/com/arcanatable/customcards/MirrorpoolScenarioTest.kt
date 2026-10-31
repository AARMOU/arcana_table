package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Mirrorpool
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*
import org.junit.Test

class MirrorpoolScenarioTest {
    @Test
    fun entersTappedAndCannotUseItsManaAbilityWhileTapped() {
        val d = customScenario(Mirrorpool)
        val p = d.player1
        val card = d.putCardInHand(p, "Mirrorpool")
        assertNull(d.playLand(p, card).error)
        val land = d.findPermanent(p, "Mirrorpool")!!

        assertTrue(d.isTapped(land))
        assertNotNull(d.activate(p, land).error)
    }

    @Test
    fun manaAbilityAddsOneColorless() {
        val d = customScenario(Mirrorpool)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mirrorpool")

        assertNull(d.activate(p, land).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(land))
    }

    @Test
    fun copiesAnInstantYouControlAndSacrificesMirrorpoolAsACost() {
        val d = customScenario(Mirrorpool)
        d.registerCard(card("Scenario Draw") {
            manaCost = "{U}"
            typeLine = "Instant"
            oracleText = "Draw a card."
            spell { effect = Effects.DrawCards(1) }
        })
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mirrorpool")
        val drawSpell = d.putCardInHand(p, "Scenario Draw")
        val startingHandSize = d.getHand(p).size
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, drawSpell).error)
        val spellOnStack = d.getTopOfStack()!!
        d.giveColorlessMana(p, 3)

        val ability = d.cardRegistry.getCard("Mirrorpool")!!.script.activatedAbilities[1]
        assertNull(d.submit(ActivateAbility(
            p, land, ability.id, targets = listOf(ChosenTarget.Spell(spellOnStack))
        )).error)
        assertNull(d.findPermanent(p, "Mirrorpool"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Mirrorpool"))
        d.resolveAll()

        assertEquals(startingHandSize + 1, d.getHand(p).size)
    }

    @Test
    fun mayChooseNewTargetsForTheCopy() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        d.registerCard(card("Scenario Growth") {
            manaCost = "{U}"
            typeLine = "Instant"
            oracleText = "Target creature gets +1/+1 until end of turn."
            spell {
                val creature = target(TargetFilter.Creature)
                effect = Effects.ModifyStats(1, 1, creature)
            }
        })
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mirrorpool")
        val originalTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val newTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val growth = d.putCardInHand(p, "Scenario Growth")
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, growth, listOf(originalTarget)).error)
        val spellOnStack = d.getTopOfStack()!!
        d.giveColorlessMana(p, 3)

        val ability = d.cardRegistry.getCard("Mirrorpool")!!.script.activatedAbilities[1]
        assertNull(d.submit(ActivateAbility(
            p, land, ability.id, targets = listOf(ChosenTarget.Spell(spellOnStack))
        )).error)
        d.resolveAll(Choices(yes = true, targets = listOf(newTarget)))

        assertEquals(3, d.projected.getPower(originalTarget))
        assertEquals(3, d.projected.getPower(newTarget))
    }

    @Test
    fun mayKeepTheOriginalTargetForTheCopy() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        d.registerCard(card("Scenario Growth") {
            manaCost = "{U}"
            typeLine = "Instant"
            oracleText = "Target creature gets +1/+1 until end of turn."
            spell {
                val creature = target(TargetFilter.Creature)
                effect = Effects.ModifyStats(1, 1, creature)
            }
        })
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mirrorpool")
        val originalTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val otherTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val growth = d.putCardInHand(p, "Scenario Growth")
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, growth, listOf(originalTarget)).error)
        val spellOnStack = d.getTopOfStack()!!
        d.giveColorlessMana(p, 3)

        val ability = d.cardRegistry.getCard("Mirrorpool")!!.script.activatedAbilities[1]
        assertNull(d.submit(ActivateAbility(
            p, land, ability.id, targets = listOf(ChosenTarget.Spell(spellOnStack))
        )).error)
        d.resolveAll(Choices(yes = false))

        assertEquals(4, d.projected.getPower(originalTarget))
        assertEquals(2, d.projected.getPower(otherTarget))
    }

    @Test
    fun cannotCopyAnInstantControlledByAnOpponent() {
        val d = customScenario(Mirrorpool)
        d.registerCard(card("Scenario Draw") {
            manaCost = "{U}"
            typeLine = "Instant"
            oracleText = "Draw a card."
            spell { effect = Effects.DrawCards(1) }
        })
        val p = d.player1
        val opponent = d.player2
        val land = d.putPermanentOnBattlefield(p, "Mirrorpool")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val drawSpell = d.putCardInHand(opponent, "Scenario Draw")
        d.giveMana(opponent, Color.BLUE)
        assertNull(d.castSpell(opponent, drawSpell).error)
        val spellOnStack = d.getTopOfStack()!!
        assertNull(d.passPriority(opponent).error)
        d.giveColorlessMana(p, 3)

        val ability = d.cardRegistry.getCard("Mirrorpool")!!.script.activatedAbilities[1]
        assertNotNull(d.submit(ActivateAbility(
            p, land, ability.id, targets = listOf(ChosenTarget.Spell(spellOnStack))
        )).error)
        assertNotNull(d.findPermanent(p, "Mirrorpool"))
        assertFalse(d.isTapped(land))
    }

    @Test
    fun createsACopyTokenOfTargetCreatureYouControl() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mirrorpool")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.giveColorlessMana(p, 5)

        val ability = d.cardRegistry.getCard("Mirrorpool")!!.script.activatedAbilities[2]
        assertNull(d.submit(ActivateAbility(
            p, land, ability.id, targets = listOf(ChosenTarget.Permanent(bear))
        )).error)
        assertNull(d.findPermanent(p, "Mirrorpool"))
        d.resolveAll()

        val bears = d.permanentsNamed(p, "Grizzly Bears")
        assertEquals(2, bears.size)
        assertTrue(bears.all { d.projected.getPower(it) == 2 })
    }

    @Test
    fun cannotCopyACreatureControlledByAnOpponent() {
        val d = customScenario(Mirrorpool, "Grizzly Bears")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mirrorpool")
        val opponentBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(p, 5)

        val ability = d.cardRegistry.getCard("Mirrorpool")!!.script.activatedAbilities[2]
        assertNotNull(d.submit(ActivateAbility(
            p, land, ability.id, targets = listOf(ChosenTarget.Permanent(opponentBear))
        )).error)
        assertNotNull(d.findPermanent(p, "Mirrorpool"))
        assertFalse(d.isTapped(land))
    }
}

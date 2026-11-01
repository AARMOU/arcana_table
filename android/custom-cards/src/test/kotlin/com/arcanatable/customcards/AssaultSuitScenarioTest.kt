package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AssaultSuit
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*
import org.junit.Test

class AssaultSuitScenarioTest {
    @Test
    fun equippedCreatureGetsBonusHasteAndCannotBeSacrificed() {
        val d = customScenario(AssaultSuit)
        val p = d.player1
        d.registerCard(card("Assault Suit Sacrifice Test Creature") {
            typeLine = "Creature — Human"
            power = 1
            toughness = 1
            activatedAbility {
                cost = Costs.SacrificeSelf
                effect = Effects.DrawCards(1)
            }
        })
        d.registerCard(card("Assault Suit Sacrifice Test Spell") {
            manaCost = "{B}"
            typeLine = "Instant"
            spell {
                val creature = target(TargetFilter.Creature)
                effect = Effects.SacrificeTarget(creature)
            }
        })

        val creature = d.putCreatureOnBattlefield(p, "Assault Suit Sacrifice Test Creature")
        val suit = d.putPermanentOnBattlefield(p, "Assault Suit")
        d.giveColorlessMana(p, 3)
        val equip = d.cardRegistry.getCard("Assault Suit")!!.script.activatedAbilities.single()
        assertNull(
            d.submit(
                ActivateAbility(p, suit, equip.id, targets = listOf(ChosenTarget.Permanent(creature))),
            ).error,
        )
        d.resolveAll(Choices(targets = listOf(creature)))

        assertEquals(3, d.projected.getPower(creature))
        assertEquals(3, d.projected.getToughness(creature))
        assertTrue(d.projected.hasKeyword(creature, Keyword.HASTE))
        assertTrue(d.abilityActions(p, creature).isEmpty())

        val sacrificeSpell = d.putCardInHand(p, "Assault Suit Sacrifice Test Spell")
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, sacrificeSpell, listOf(creature)).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Assault Suit Sacrifice Test Creature"))
    }

    @Test
    fun opponentUpkeepMayTransferCreatureUntapsItAndItCannotAttackItsOwner() {
        val d = customScenario(AssaultSuit, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val creature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val suit = d.putPermanentOnBattlefield(p, "Assault Suit")
        d.giveColorlessMana(p, 3)
        val equip = d.cardRegistry.getCard("Assault Suit")!!.script.activatedAbilities.single()
        assertNull(
            d.submit(
                ActivateAbility(p, suit, equip.id, targets = listOf(ChosenTarget.Permanent(creature))),
            ).error,
        )
        d.resolveAll(Choices(targets = listOf(creature)))
        d.tapPermanent(creature)

        d.passPriorityUntil(Step.UPKEEP, activePlayer = opponent)
        assertEquals(opponent, d.activePlayer)
        d.resolveAll(Choices(yes = true))

        assertEquals(opponent, d.projected.getController(creature))
        assertFalse(d.isTapped(creature))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNotNull(d.declareAttackers(opponent, listOf(creature), p).error)

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        assertEquals(p, d.projected.getController(creature))
    }

    @Test
    fun decliningOpponentUpkeepTransferLeavesCreatureControlledAndTapped() {
        val d = customScenario(AssaultSuit, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val creature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val suit = d.putPermanentOnBattlefield(p, "Assault Suit")
        d.giveColorlessMana(p, 3)
        val equip = d.cardRegistry.getCard("Assault Suit")!!.script.activatedAbilities.single()
        assertNull(
            d.submit(
                ActivateAbility(p, suit, equip.id, targets = listOf(ChosenTarget.Permanent(creature))),
            ).error,
        )
        d.resolveAll(Choices(targets = listOf(creature)))
        d.tapPermanent(creature)

        d.passPriorityUntil(Step.UPKEEP, activePlayer = opponent)
        d.resolveAll(Choices(yes = false))

        assertEquals(p, d.projected.getController(creature))
        assertTrue(d.isTapped(creature))
    }
}

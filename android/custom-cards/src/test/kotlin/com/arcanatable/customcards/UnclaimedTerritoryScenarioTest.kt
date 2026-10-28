package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UnclaimedTerritory
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

class UnclaimedTerritoryScenarioTest {
    private fun setup(): Pair<GameTestDriver, EntityId> {
        val d = customScenario(UnclaimedTerritory, "Grizzly Bears", "Llanowar Elves", "Elvish Visionary", "Sol Ring")
        val land = d.putCardInHand(d.player1, "Unclaimed Territory")
        assertNull(d.playLand(d.player1, land).error)
        val choice = d.pendingDecision
        assertTrue("Type must be chosen as the land enters", choice is ChooseOptionDecision)
        choice as ChooseOptionDecision
        val bear = choice.options.indexOfFirst { it.equals("Bear", ignoreCase = true) }
        assertTrue("Bear is an available creature type", bear >= 0)
        assertNull(d.submitDecision(d.player1, OptionChosenResponse(choice.id, bear)).error)
        d.resolveAll()
        return d to d.findPermanent(d.player1, "Unclaimed Territory")!!
    }

    private fun floatGreen(d: GameTestDriver, land: EntityId) {
        val ability = d.cardRegistry.getCard("Unclaimed Territory")!!.script.activatedAbilities[1]
        assertNull(d.submit(ActivateAbility(d.player1, land, ability.id, manaColorChoice = Color.GREEN)).error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertEquals("One tracked restricted unit", 1, d.manaPool(d.player1).restrictedMana.size)
    }
    @Test
    fun coloredManaAbilityUsesTheChosenCreatureTypeEffect() {
        val d = customScenario(UnclaimedTerritory, "Forest")
        val territory = d.putCardInHand(d.player1, "Unclaimed Territory")
        assertNull(d.playLand(d.player1, territory).error)
        d.resolveAll()
        val ability = d.cardRegistry.getCard("Unclaimed Territory")!!.script.activatedAbilities[1]
        val result = d.submit(ActivateAbility(d.player1, territory, ability.id, manaColorChoice = Color.GREEN))
        assertNull(result.error)
        d.resolveAll(Choices(color = Color.GREEN))
        val pool = d.manaPool(d.player1)
        assertTrue("mana pool after activation: $pool", pool.green == 1 || pool.restrictedMana.any { it.color == Color.GREEN })
    }

    @Test fun chosenBearCreatureConsumesTheRestrictedColoredMana() {
        val (d, land) = setup()
        floatGreen(d, land)
        val bear = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, bear).error)
        assertTrue(d.manaPool(d.player1).restrictedMana.isEmpty())
        assertEquals(0, d.manaPool(d.player1).colorless)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player1, "Grizzly Bears"))
    }

    @Test fun wrongCreatureTypeIsRejectedWithoutSpendingAndUnrestrictedManaCanPayIt() {
        val (d, land) = setup()
        floatGreen(d, land)
        val elf = d.putCardInHand(d.player1, "Llanowar Elves")
        val before = d.manaPool(d.player1)
        assertNotNull(d.castSpell(d.player1, elf).error)
        assertEquals(before, d.manaPool(d.player1))
        assertTrue(elf in d.state.getZone(d.player1, Zone.HAND))
        d.giveMana(d.player1, Color.GREEN)
        assertNull(d.castSpell(d.player1, elf).error)
        assertEquals("Restricted mana cannot be spent on an Elf", 1, d.manaPool(d.player1).restrictedMana.size)
        assertEquals(0, d.manaPool(d.player1).green)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player1, "Llanowar Elves"))
    }

    @Test fun restrictedManaCannotPayTheGenericPartOfAWrongTypeCreature() {
        val (d, land) = setup()
        floatGreen(d, land)
        d.giveMana(d.player1, Color.GREEN)
        val elf = d.putCardInHand(d.player1, "Elvish Visionary")
        val before = d.manaPool(d.player1)
        assertNotNull("Matching colored pip does not launder restricted mana into generic cost", d.castSpell(d.player1, elf).error)
        assertEquals(before, d.manaPool(d.player1))
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, elf).error)
        assertEquals(1, d.manaPool(d.player1).restrictedMana.size)
        assertEquals(0, d.manaPool(d.player1).green)
        assertEquals(0, d.manaPool(d.player1).colorless)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player1, "Elvish Visionary"))
    }

    @Test fun restrictedManaCannotPayForANoncreatureEvenWhenItHasTheChosenSubtype() {
        val (d, land) = setup()
        val probe = card("Territory Kindred Bear Probe") {
            manaCost = "{G}"
            typeLine = "Kindred Sorcery — Bear"
            spell { effect = Effects.GainLife(1) }
        }
        d.registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(probe))))
        floatGreen(d, land)
        val spell = d.putCardInHand(d.player1, probe.name)
        val before = d.manaPool(d.player1)
        assertNotNull(d.castSpell(d.player1, spell).error)
        assertEquals(before, d.manaPool(d.player1))
        d.giveMana(d.player1, Color.GREEN)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertEquals(21, d.getLifeTotal(d.player1))
        assertEquals(1, d.manaPool(d.player1).restrictedMana.size)
    }

    @Test fun unrestrictedColorlessModeCastsAnArtifactAndBothModesRequireUntappedLand() {
        val (d, land) = setup()
        assertNull(d.activate(d.player1, land, index = 0).error)
        assertEquals(1, d.manaPool(d.player1).colorless)
        assertTrue(d.manaPool(d.player1).restrictedMana.isEmpty())
        assertNotNull(d.activate(d.player1, land, index = 0).error)
        assertNotNull(d.activate(d.player1, land, index = 1).error)
        assertEquals(1, d.manaPool(d.player1).colorless)
        val ring = d.putCardInHand(d.player1, "Sol Ring")
        assertNull(d.castSpell(d.player1, ring).error)
        d.resolveAll()
        assertEquals(0, d.manaPool(d.player1).colorless)
        assertNotNull(d.findPermanent(d.player1, "Sol Ring"))
    }
}

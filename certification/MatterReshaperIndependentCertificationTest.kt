package com.arcanatable.customcards

// Trusted-main, real-Argentum acceptance scenarios for Matter Reshaper.
// "{2}{C}: Creature — Eldrazi, 3/2. When this creature dies, reveal the top card of your library.
// You may put it onto the battlefield if it is a permanent card with mana value 3 or less;
// otherwise put it into your hand." A declined optional placement also puts the card in hand.
import com.arcanatable.customcards.cards.MatterReshaper
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MatterReshaperIndependentCertificationTest {
    private fun killWithShock(d: GameTestDriver, player: EntityId, creature: EntityId, choices: Choices = Choices()) {
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpellWithTargets(player, shock, listOf(ChosenTarget.Permanent(creature))).error)
        d.resolveAll(choices)
    }

    @Test
    fun castingRequiresTwoGenericAndOneColorlessManaAndIsAThreeTwoEldrazi() {
        val d = customScenario(MatterReshaper, "Phyrexian Arena")
        val player = d.player1
        val card = d.putCardInHand(player, "Matter Reshaper")
        d.putCardOnTopOfLibrary(player, "Phyrexian Arena")
        d.giveMana(player, Color.RED, 3)

        assertNotNull("Three colored mana cannot pay the {C} symbol", d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))
        assertEquals(3, d.manaPool(player).red)
        assertEquals(0, d.manaPool(player).colorless)

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        val reshaper = d.permanentsNamed(player, "Matter Reshaper").single()
        assertTrue(d.projected.isCreature(reshaper))
        assertTrue(d.projected.hasSubtype(reshaper, "ELDRAZI"))
        assertEquals(3, d.projected.getPower(reshaper))
        assertEquals(2, d.projected.getToughness(reshaper))
        assertTrue("Entering the battlefield does not trigger its death ability",
            d.zoneNames(player, Zone.LIBRARY).contains("Phyrexian Arena"))
    }

    @Test
    fun dyingRevealsAndPutsAThreeManaPermanentOntoTheBattlefieldWithoutCastingIt() {
        val d = customScenario(MatterReshaper, "Phyrexian Arena", "Shock")
        val player = d.player1
        val reshaper = d.putCreatureOnBattlefield(player, "Matter Reshaper")
        val topCard = d.putCardOnTopOfLibrary(player, "Phyrexian Arena")

        killWithShock(d, player, reshaper)

        assertTrue(d.getGraveyard(player).contains(reshaper))
        assertTrue("The revealed mana value 3 permanent enters under its owner's control",
            d.permanentsNamed(player, "Phyrexian Arena").contains(topCard))
        assertFalse(d.getHand(player).contains(topCard))
        assertFalse(d.zoneNames(player, Zone.LIBRARY).contains("Phyrexian Arena"))
    }

    @Test
    fun decliningToPutAnEligiblePermanentOntoTheBattlefieldPutsItIntoHand() {
        val d = customScenario(MatterReshaper, "Phyrexian Arena", "Shock")
        val player = d.player1
        val reshaper = d.putCreatureOnBattlefield(player, "Matter Reshaper")
        val topCard = d.putCardOnTopOfLibrary(player, "Phyrexian Arena")

        killWithShock(d, player, reshaper, Choices(yes = false))

        assertTrue(d.getGraveyard(player).contains(reshaper))
        assertTrue(d.getHand(player).contains(topCard))
        assertFalse(d.getPermanents(player).contains(topCard))
        assertFalse(d.zoneNames(player, Zone.LIBRARY).contains("Phyrexian Arena"))
    }

    @Test
    fun aPermanentWithManaValueGreaterThanThreeGoesIntoHand() {
        val d = customScenario(MatterReshaper, "Serra Angel", "Shock")
        val player = d.player1
        val reshaper = d.putCreatureOnBattlefield(player, "Matter Reshaper")
        val topCard = d.putCardOnTopOfLibrary(player, "Serra Angel")

        killWithShock(d, player, reshaper)

        assertTrue(d.getGraveyard(player).contains(reshaper))
        assertTrue(d.getHand(player).contains(topCard))
        assertFalse(d.getPermanents(player).contains(topCard))
        assertFalse(d.zoneNames(player, Zone.LIBRARY).contains("Serra Angel"))
    }

    @Test
    fun aNonpermanentCardGoesIntoHand() {
        val d = customScenario(MatterReshaper, "Shock")
        val player = d.player1
        val reshaper = d.putCreatureOnBattlefield(player, "Matter Reshaper")
        val topCard = d.putCardOnTopOfLibrary(player, "Shock")

        killWithShock(d, player, reshaper)

        assertTrue(d.getGraveyard(player).contains(reshaper))
        assertTrue(d.getHand(player).contains(topCard))
        assertFalse(d.getPermanents(player).contains(topCard))
        assertFalse(d.zoneNames(player, Zone.LIBRARY).contains("Shock"))
    }

    @Test
    fun anotherCreatureDyingDoesNotTriggerMatterReshaper() {
        val d = customScenario(MatterReshaper, "Phyrexian Arena", "Grizzly Bears", "Shock")
        val player = d.player1
        val reshaper = d.putCreatureOnBattlefield(player, "Matter Reshaper")
        val bears = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putCardOnTopOfLibrary(player, "Phyrexian Arena")

        killWithShock(d, player, bears)

        assertTrue(d.getPermanents(player).contains(reshaper))
        assertFalse(d.getGraveyard(player).contains(reshaper))
        assertTrue(d.zoneNames(player, Zone.LIBRARY).contains("Phyrexian Arena"))
        assertTrue(d.permanentsNamed(player, "Phyrexian Arena").isEmpty())
    }
}

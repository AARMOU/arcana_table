package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HullBreach
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class HullBreachIndependentCertificationTest {
    private val name = "Hull Breach"

    private fun GameTestDriver.payCost(player: EntityId) {
        giveMana(player, Color.RED)
        giveMana(player, Color.GREEN)
    }

    private fun GameTestDriver.castMode(
        player: EntityId,
        spell: EntityId,
        modes: List<Int>,
        targetsByMode: List<List<ChosenTarget>>,
    ) = submit(
        CastSpell(
            playerId = player,
            cardId = spell,
            targets = targetsByMode.flatten(),
            paymentStrategy = PaymentStrategy.FromPool,
            chosenModes = modes,
            modeTargetsOrdered = targetsByMode,
        ),
    )

    @Test
    fun artifactModeDestroysOnlyItsTargetArtifact() {
        val d = customScenario(HullBreach, "Sol Ring", "Phyrexian Arena")
        val p = d.player1
        val target = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val otherArtifact = d.putPermanentOnBattlefield(p, "Sol Ring")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNull(d.castMode(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(target)))).error)
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(target))
        assertTrue(d.getPermanents(p).contains(otherArtifact))
        assertTrue(d.getPermanents(d.player2).contains(enchantment))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Sol Ring"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
    }

    @Test
    fun enchantmentModeDestroysOnlyItsTargetEnchantment() {
        val d = customScenario(HullBreach, "Sol Ring", "Phyrexian Arena")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val target = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val otherEnchantment = d.putPermanentOnBattlefield(p, "Phyrexian Arena")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNull(d.castMode(p, spell, listOf(1), listOf(listOf(ChosenTarget.Permanent(target)))).error)
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(target))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getPermanents(p).contains(otherEnchantment))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Phyrexian Arena"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
    }

    @Test
    fun thirdModeDestroysItsArtifactAndEnchantmentTargets() {
        val d = customScenario(HullBreach, "Sol Ring", "Phyrexian Arena")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val enchantment = d.putPermanentOnBattlefield(p, "Phyrexian Arena")
        val otherArtifact = d.putPermanentOnBattlefield(p, "Sol Ring")
        val otherEnchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNull(
            d.castMode(
                p,
                spell,
                listOf(2),
                listOf(listOf(ChosenTarget.Permanent(artifact), ChosenTarget.Permanent(enchantment))),
            ).error,
        )
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(artifact))
        assertFalse(d.getPermanents(p).contains(enchantment))
        assertTrue(d.getPermanents(p).contains(otherArtifact))
        assertTrue(d.getPermanents(d.player2).contains(otherEnchantment))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Sol Ring"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Phyrexian Arena"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
    }

    @Test
    fun artifactModeCannotTargetAnEnchantment() {
        val d = customScenario(HullBreach, "Phyrexian Arena")
        val p = d.player1
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNotNull(d.castMode(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(enchantment)))).error)
        assertTrue(d.getPermanents(d.player2).contains(enchantment))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun enchantmentModeCannotTargetAnArtifact() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNotNull(d.castMode(p, spell, listOf(1), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun thirdModeRequiresAnArtifactAndAnEnchantmentTarget() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val firstArtifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val secondArtifact = d.putPermanentOnBattlefield(p, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNotNull(
            d.castMode(
                p,
                spell,
                listOf(2),
                listOf(listOf(ChosenTarget.Permanent(firstArtifact), ChosenTarget.Permanent(secondArtifact))),
            ).error,
        )
        assertTrue(d.getPermanents(d.player2).contains(firstArtifact))
        assertTrue(d.getPermanents(p).contains(secondArtifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun artifactModeRequiresATarget() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNotNull(d.castMode(p, spell, listOf(0), listOf(emptyList())).error)
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun onlyOneModeCanBeChosen() {
        val d = customScenario(HullBreach, "Sol Ring", "Phyrexian Arena")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNotNull(
            d.castMode(
                p,
                spell,
                listOf(0, 1),
                listOf(
                    listOf(ChosenTarget.Permanent(artifact)),
                    listOf(ChosenTarget.Permanent(enchantment)),
                ),
            ).error,
        )
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getPermanents(d.player2).contains(enchantment))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun oneModeMustBeChosen() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNotNull(d.castMode(p, spell, emptyList(), emptyList()).error)
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun greenManaCannotSubstituteForTheRedManaCost() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castMode(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun redManaCannotSubstituteForTheGreenManaCost() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castMode(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.payCost(p)

        assertNotNull(d.castMode(p, spell, listOf(0), listOf(listOf(ChosenTarget.Permanent(artifact)))).error)
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun sorceryCannotBeCastWhileAnotherSpellIsOnTheStack() {
        val d = customScenario(HullBreach, "Sol Ring")
        val p = d.player1
        val firstArtifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val secondArtifact = d.putPermanentOnBattlefield(p, "Sol Ring")
        val firstSpell = d.putCardInHand(p, name)
        val secondSpell = d.putCardInHand(p, name)
        d.payCost(p)

        assertNull(d.castMode(p, firstSpell, listOf(0), listOf(listOf(ChosenTarget.Permanent(firstArtifact)))).error)
        d.payCost(p)

        assertNotNull(d.castMode(p, secondSpell, listOf(0), listOf(listOf(ChosenTarget.Permanent(secondArtifact)))).error)
        assertTrue(d.getPermanents(d.player2).contains(firstArtifact))
        assertTrue(d.getPermanents(p).contains(secondArtifact))
        assertTrue(d.getHand(p).contains(secondSpell))

        d.resolveAll()
        assertFalse(d.getPermanents(d.player2).contains(firstArtifact))
        assertTrue(d.getPermanents(p).contains(secondArtifact))
    }
}

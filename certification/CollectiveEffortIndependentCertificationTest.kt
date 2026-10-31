package com.arcanatable.customcards

// Independent Oracle certification for Collective Effort (#555), registered for suite issue #568.
// Oracle: "{1}{W}{W} Sorcery. Escalate—Tap an untapped creature you control. Choose one or more:
// destroy target creature with power 4 or greater; destroy target enchantment; or put a +1/+1
// counter on each creature target player controls."
import com.arcanatable.customcards.cards.CollectiveEffort
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import org.junit.Assert.*
import org.junit.Test

class CollectiveEffortIndependentCertificationTest {
    private val name = "Collective Effort"

    private fun GameTestDriver.giveFullCost(player: EntityId) {
        giveMana(player, Color.WHITE, 2)
        giveColorlessMana(player, 1)
    }

    private fun GameTestDriver.castCollectiveEffort(
        player: EntityId,
        modes: List<Int>,
        modeTargets: List<List<ChosenTarget>>,
        tappedCreatures: List<EntityId> = emptyList(),
    ): Pair<EntityId, com.wingedsheep.engine.core.ExecutionResult> {
        val spell = putCardInHand(player, name)
        val result = submit(
            CastSpell(
                playerId = player,
                cardId = spell,
                targets = modeTargets.flatten(),
                paymentStrategy = PaymentStrategy.FromPool,
                additionalCostPayment = tappedCreatures.takeIf { it.isNotEmpty() }
                    ?.let { AdditionalCostPayment(tappedPermanents = it) },
                chosenModes = modes,
                modeTargetsOrdered = modeTargets,
            ),
        )
        return spell to result
    }

    @Test
    fun firstModeDestroysTargetCreatureWithPowerAtLeastFour() {
        val d = customScenario(CollectiveEffort, "Serra Angel")
        val player = d.player1
        val victim = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        d.giveFullCost(player)

        val (_, cast) = d.castCollectiveEffort(
            player,
            listOf(0),
            listOf(listOf(ChosenTarget.Permanent(victim))),
        )
        assertNull(cast.error)
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(victim))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Serra Angel"))
    }

    @Test
    fun firstModeCannotTargetACreatureWithPowerBelowFour() {
        val d = customScenario(CollectiveEffort, "Grizzly Bears")
        val player = d.player1
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveFullCost(player)

        val (spell, cast) = d.castCollectiveEffort(
            player,
            listOf(0),
            listOf(listOf(ChosenTarget.Permanent(victim))),
        )
        assertNotNull(cast.error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Collective Effort"))
        assertTrue(d.getPermanents(d.player2).contains(victim))
        assertFalse(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.getHand(player).contains(spell))
    }

    @Test
    fun secondModeDestroysTargetEnchantment() {
        val d = customScenario(CollectiveEffort, "Phyrexian Arena")
        val player = d.player1
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        d.giveFullCost(player)

        val (_, cast) = d.castCollectiveEffort(
            player,
            listOf(1),
            listOf(listOf(ChosenTarget.Permanent(enchantment))),
        )
        assertNull(cast.error)
        d.resolveAll()

        assertFalse(d.getPermanents(d.player2).contains(enchantment))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Phyrexian Arena"))
    }

    @Test
    fun thirdModeAddsACounterToEachCreatureControlledByTheTargetPlayerOnly() {
        val d = customScenario(CollectiveEffort, "Grizzly Bears", "Giant Spider")
        val player = d.player1
        val ownBear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val targetBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val targetSpider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.giveFullCost(player)

        val (_, cast) = d.castCollectiveEffort(
            player,
            listOf(2),
            listOf(listOf(ChosenTarget.Player(d.player2))),
        )
        assertNull(cast.error)
        d.resolveAll()

        assertEquals(2, d.projected.getPower(ownBear))
        assertEquals(3, d.projected.getPower(targetBear))
        assertEquals(3, d.projected.getToughness(targetBear))
        assertEquals(3, d.projected.getPower(targetSpider))
        assertEquals(4, d.projected.getToughness(targetSpider))
    }

    @Test
    fun choosingTwoModesRequiresAndPaysOneTapCost() {
        val d = customScenario(CollectiveEffort, "Serra Angel", "Grizzly Bears")
        val player = d.player1
        val tapper = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val targetBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveFullCost(player)

        val (_, cast) = d.castCollectiveEffort(
            player,
            listOf(0, 2),
            listOf(
                listOf(ChosenTarget.Permanent(victim)),
                listOf(ChosenTarget.Player(d.player2)),
            ),
            listOf(tapper),
        )
        assertNull(cast.error)
        d.resolveAll()

        assertTrue(d.isTapped(tapper))
        assertFalse(d.getPermanents(d.player2).contains(victim))
        assertEquals(3, d.projected.getPower(targetBear))
    }

    @Test
    fun choosingAllThreeModesRequiresAndPaysTwoDistinctTapCosts() {
        val d = customScenario(CollectiveEffort, "Serra Angel", "Phyrexian Arena", "Grizzly Bears", "Giant Spider")
        val player = d.player1
        val firstTapper = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val secondTapper = d.putCreatureOnBattlefield(player, "Giant Spider")
        val victim = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val targetBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveFullCost(player)

        val (_, cast) = d.castCollectiveEffort(
            player,
            listOf(0, 1, 2),
            listOf(
                listOf(ChosenTarget.Permanent(victim)),
                listOf(ChosenTarget.Permanent(enchantment)),
                listOf(ChosenTarget.Player(d.player2)),
            ),
            listOf(firstTapper, secondTapper),
        )
        assertNull(cast.error)
        d.resolveAll()

        assertTrue(d.isTapped(firstTapper))
        assertTrue(d.isTapped(secondTapper))
        assertFalse(d.getPermanents(d.player2).contains(victim))
        assertFalse(d.getPermanents(d.player2).contains(enchantment))
        assertEquals(3, d.projected.getPower(targetBear))
    }

    @Test
    fun multipleModesCannotBeCastWithoutTheRequiredTapCost() {
        val d = customScenario(CollectiveEffort, "Serra Angel", "Grizzly Bears")
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val targetBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveFullCost(player)

        val (spell, cast) = d.castCollectiveEffort(
            player,
            listOf(0, 2),
            listOf(
                listOf(ChosenTarget.Permanent(victim)),
                listOf(ChosenTarget.Player(d.player2)),
            ),
        )
        assertNotNull(cast.error)

        assertTrue(d.getHand(player).contains(spell))
        assertFalse(d.isTapped(creature))
        assertTrue(d.getPermanents(d.player2).contains(victim))
        assertEquals(2, d.projected.getPower(targetBear))
    }

    @Test
    fun onlyYourUntappedCreatureCanPayEscalate() {
        val d = customScenario(CollectiveEffort, "Serra Angel", "Grizzly Bears")
        val player = d.player1
        val opponentCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val targetBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveFullCost(player)

        val (spell, cast) = d.castCollectiveEffort(
            player,
            listOf(0, 2),
            listOf(
                listOf(ChosenTarget.Permanent(victim)),
                listOf(ChosenTarget.Player(d.player2)),
            ),
            listOf(opponentCreature),
        )
        assertNotNull(cast.error)

        assertTrue(d.getHand(player).contains(spell))
        assertFalse(d.isTapped(opponentCreature))
        assertTrue(d.getPermanents(d.player2).contains(victim))
        assertEquals(2, d.projected.getPower(targetBear))
    }

    @Test
    fun coloredAndGenericManaRequirementsAreBothMandatory() {
        val d = customScenario(CollectiveEffort, "Phyrexian Arena")
        val player = d.player1
        val enchantment = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 2)

        val (spell, cast) = d.castCollectiveEffort(
            player,
            listOf(1),
            listOf(listOf(ChosenTarget.Permanent(enchantment))),
        )
        assertNotNull(cast.error)

        assertTrue(d.getHand(player).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(enchantment))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(CollectiveEffort, "Phyrexian Arena")
        val player = d.player1
        val enchantment = d.putPermanentOnBattlefield(player, "Phyrexian Arena")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveFullCost(player)

        val (spell, cast) = d.castCollectiveEffort(
            player,
            listOf(1),
            listOf(listOf(ChosenTarget.Permanent(enchantment))),
        )
        assertNotNull(cast.error)

        assertTrue(d.getHand(player).contains(spell))
        assertTrue(d.getPermanents(player).contains(enchantment))
    }
}

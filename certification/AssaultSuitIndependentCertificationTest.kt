package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AssaultSuit
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class AssaultSuitIndependentCertificationTest {
    private val name = "Assault Suit"

    private fun equip(d: GameTestDriver, player: EntityId, suit: EntityId, creature: EntityId) =
        d.submit(
            ActivateAbility(
                player,
                suit,
                d.cardRegistry.getCard(name)!!.script.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(creature)),
            )
        )

    private fun assertEquipped(d: GameTestDriver, suit: EntityId, creature: EntityId) {
        assertEquals(creature, d.state.getEntity(suit)?.get<AttachedToComponent>()?.targetId)
    }

    @Test
    fun castingAssaultSuitRequiresFourGenericMana() {
        val d = customScenario(AssaultSuit)
        val player = d.player1
        val suit = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, suit).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains(name))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, suit).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(suit))
    }

    @Test
    fun equipRequiresThreeManaAndGrantsTheBonusAndHaste() {
        val d = customScenario(AssaultSuit, "Grizzly Bears")
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val suit = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.giveColorlessMana(player, 2)

        assertNotNull(equip(d, player, suit, creature).error)
        assertEquals(2, d.manaPool(player).colorless)
        assertNull(d.state.getEntity(suit)?.get<AttachedToComponent>())

        d.giveColorlessMana(player, 1)
        assertNull(equip(d, player, suit, creature).error)
        d.resolveAll()
        assertEquipped(d, suit, creature)
        assertEquals(4, d.projected.getPower(creature))
        assertEquals(4, d.projected.getToughness(creature))
        assertTrue(d.projected.hasKeyword(creature, Keyword.HASTE))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(creature), d.player2).error)
    }

    @Test
    fun equipCanOnlyTargetACreatureYouControl() {
        val d = customScenario(AssaultSuit, "Grizzly Bears")
        val player = d.player1
        val opponentCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val suit = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.giveColorlessMana(player, 3)

        assertNotNull(equip(d, player, suit, opponentCreature).error)
        assertEquals(3, d.manaPool(player).colorless)
        assertNull(d.state.getEntity(suit)?.get<AttachedToComponent>())
    }

    @Test
    fun equipCanOnlyBeActivatedAtSorceryTiming() {
        val d = customScenario(AssaultSuit, "Grizzly Bears")
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val suit = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.giveColorlessMana(player, 3)

        assertNotNull(equip(d, player, suit, creature).error)
        assertEquals(3, d.manaPool(player).colorless)
        assertNull(d.state.getEntity(suit)?.get<AttachedToComponent>())
    }

    @Test
    fun opponentMayGainControlAtTheirUpkeepAndCannotAttackTheSuitControllerOrTheirPlaneswalker() {
        val d = customScenario(AssaultSuit, "Grizzly Bears", "Jace Beleren")
        val player = d.player1
        val opponent = d.player2
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val otherCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val planeswalker = d.putPermanentOnBattlefield(player, "Jace Beleren")
        val suit = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.giveColorlessMana(player, 3)
        assertNull(equip(d, player, suit, creature).error)
        d.resolveAll()

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        d.resolveAll()
        assertEquals(player, d.getController(creature))
        d.tapPermanent(creature)
        assertTrue(d.isTapped(creature))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = opponent)
        assertTrue(d.isTapped(creature))
        d.resolveAll(Choices(yes = true))

        assertEquals(opponent, d.getController(creature))
        assertFalse(d.isTapped(creature))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNotNull(d.declareAttackers(opponent, mapOf(creature to player)).error)
        assertNotNull(d.declareAttackers(opponent, mapOf(creature to planeswalker)).error)
        d.removeSummoningSickness(otherCreature)
        assertNull(d.declareAttackers(opponent, mapOf(otherCreature to planeswalker)).error)
        assertEquals(opponent, d.getController(creature))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        d.resolveAll()
        assertEquals(player, d.getController(creature))
    }

    @Test
    fun decliningControlAtAnOpponentUpkeepLeavesTheCreatureTappedAndWithItsController() {
        val d = customScenario(AssaultSuit, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val suit = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.giveColorlessMana(player, 3)
        assertNull(equip(d, player, suit, creature).error)
        d.resolveAll()

        d.tapPermanent(creature)
        d.passPriorityUntil(Step.UPKEEP, activePlayer = opponent)
        assertTrue(d.isTapped(creature))
        d.resolveAll(Choices(yes = false))

        assertEquals(player, d.getController(creature))
        assertTrue(d.isTapped(creature))
    }

    @Test
    fun equippedCreatureCannotBeSacrificed() {
        val d = customScenario(AssaultSuit, "Grizzly Bears", "Diabolic Edict")
        val player = d.player1
        val opponent = d.player2
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val suit = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.giveColorlessMana(player, 3)
        assertNull(equip(d, player, suit, creature).error)
        d.resolveAll()

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val edict = d.putCardInHand(opponent, "Diabolic Edict")
        d.giveMana(opponent, Color.BLACK)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, edict, listOf(player)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").contains(creature))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }
}

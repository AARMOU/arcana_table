package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BoneSabres
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import org.junit.Assert.*
import org.junit.Test

class BoneSabresIndependentCertificationTest {
    private val name = "Bone Sabres"

    private fun GameTestDriver.counterCount(permanent: EntityId): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun castSabres(d: GameTestDriver, player: EntityId): EntityId {
        val sabres = d.putCardInHand(player, name)
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 2)
        assertNull(d.castSpell(player, sabres).error)
        d.resolveAll()
        return sabres
    }

    private fun equip(d: GameTestDriver, player: EntityId, sabres: EntityId, creature: EntityId) =
        d.submit(
            ActivateAbility(
                player,
                sabres,
                d.cardRegistry.getCard(name)!!.script.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(creature)),
            )
        )

    @Test
    fun castingRequiresTwoGenericAndOneGreenMana() {
        val d = customScenario(BoneSabres)
        val player = d.player1
        val sabres = d.putCardInHand(player, name)
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)

        assertNotNull(d.castSpell(player, sabres).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains(name))
        assertTrue(d.permanentsNamed(player, name).isEmpty())

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, sabres).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(sabres))
        assertFalse(d.zoneNames(player, Zone.HAND).contains(name))
    }

    @Test
    fun threeGenericManaCannotPayTheGreenSymbol() {
        val d = customScenario(BoneSabres)
        val player = d.player1
        val sabres = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, sabres).error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains(name))
        assertTrue(d.permanentsNamed(player, name).isEmpty())
    }

    @Test
    fun equipCostsThreeManaAndAttachesToYourCreature() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sabres = castSabres(d, player)
        d.giveColorlessMana(player, 3)

        assertNull(equip(d, player, sabres, bear).error)
        d.resolveAll()

        assertEquals(bear, d.state.getEntity(sabres)?.get<AttachedToComponent>()?.targetId)
        assertEquals(0, d.counterCount(bear))
    }

    @Test
    fun twoManaCannotPayTheEquipCost() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sabres = castSabres(d, player)
        d.giveColorlessMana(player, 2)

        assertNotNull(equip(d, player, sabres, bear).error)

        assertEquals(2, d.manaPool(player).colorless)
        assertNull(d.state.getEntity(sabres)?.get<AttachedToComponent>())
    }

    @Test
    fun equipCannotTargetAnOpponentControlledCreature() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val player = d.player1
        val opponentBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val sabres = castSabres(d, player)
        d.giveColorlessMana(player, 3)

        assertNotNull(equip(d, player, sabres, opponentBear).error)

        assertEquals(3, d.manaPool(player).colorless)
        assertNull(d.state.getEntity(sabres)?.get<AttachedToComponent>())
    }

    @Test
    fun equipCanOnlyBeActivatedAtSorceryTiming() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sabres = castSabres(d, player)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.giveColorlessMana(player, 3)

        assertNotNull(equip(d, player, sabres, bear).error)

        assertEquals(3, d.manaPool(player).colorless)
        assertNull(d.state.getEntity(sabres)?.get<AttachedToComponent>())
    }

    @Test
    fun equippedCreatureGetsExactlyFourPlusOnePlusOneCountersWhenItAttacks() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sabres = castSabres(d, player)
        d.giveColorlessMana(player, 3)
        assertNull(equip(d, player, sabres, bear).error)
        d.resolveAll()
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(bear), d.player2).error)
        d.resolveAll()

        assertEquals(4, d.counterCount(bear))
        assertEquals(6, d.projected.getPower(bear))
        assertEquals(6, d.projected.getToughness(bear))
    }

    @Test
    fun attackingWithAnUnequippedCreatureDoesNotAddCounters() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val player = d.player1
        val equippedBear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val otherBear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sabres = castSabres(d, player)
        d.giveColorlessMana(player, 3)
        assertNull(equip(d, player, sabres, equippedBear).error)
        d.resolveAll()
        d.removeSummoningSickness(equippedBear)
        d.removeSummoningSickness(otherBear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(otherBear), d.player2).error)
        d.resolveAll()

        assertEquals(0, d.counterCount(equippedBear))
        assertEquals(0, d.counterCount(otherBear))
    }

    @Test
    fun equippedCreatureDoesNotGetCountersWhenItDoesNotAttack() {
        val d = customScenario(BoneSabres, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sabres = castSabres(d, player)
        d.giveColorlessMana(player, 3)
        assertNull(equip(d, player, sabres, bear).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, emptyList(), d.player2).error)
        d.resolveAll()

        assertEquals(0, d.counterCount(bear))
    }
}

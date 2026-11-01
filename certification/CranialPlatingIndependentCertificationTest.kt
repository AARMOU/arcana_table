package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CranialPlating
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class CranialPlatingIndependentCertificationTest {
    private val name = "Cranial Plating"

    private fun activateAttachAbility(
        d: GameTestDriver,
        player: EntityId,
        plating: EntityId,
        creature: EntityId,
    ) = d.submit(
        ActivateAbility(
            player,
            plating,
            d.cardRegistry.getCard(name)!!.script.activatedAbilities[0].id,
            targets = listOf(ChosenTarget.Permanent(creature)),
        ),
    )

    private fun activateEquip(
        d: GameTestDriver,
        player: EntityId,
        plating: EntityId,
        creature: EntityId,
    ) = d.submit(
        ActivateAbility(
            player,
            plating,
            d.cardRegistry.getCard(name)!!.script.activatedAbilities[1].id,
            targets = listOf(ChosenTarget.Permanent(creature)),
        ),
    )

    private fun assertAttachedTo(d: GameTestDriver, plating: EntityId, creature: EntityId) {
        assertEquals(creature, d.state.getEntity(plating)?.get<AttachedToComponent>()?.targetId)
    }

    @Test
    fun castingRequiresTwoGenericManaAndPutsAnArtifactOntoTheBattlefield() {
        val d = customScenario(CranialPlating)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 1)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains(name))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        val plating = d.findPermanent(player, name)
        assertNotNull(plating)
        assertTrue(d.projected.hasType(plating!!, "ARTIFACT"))
    }

    @Test
    fun eachArtifactYouControlAddsOnePowerToTheEquippedCreature() {
        val d = customScenario(CranialPlating, "Grizzly Bears", "Sol Ring", "Bonesplitter")
        val player = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(player, name)
        assertEquals(2, d.projected.getPower(bear))

        d.giveColorlessMana(player, 1)
        assertNull(activateEquip(d, player, plating, bear).error)
        d.resolveAll()
        assertAttachedTo(d, plating, bear)
        assertEquals(3, d.projected.getPower(bear))

        d.putPermanentOnBattlefield(player, "Sol Ring")
        assertEquals(4, d.projected.getPower(bear))
        d.putPermanentOnBattlefield(player, "Bonesplitter")
        assertEquals(5, d.projected.getPower(bear))
        d.putPermanentOnBattlefield(opponent, "Sol Ring")
        assertEquals(5, d.projected.getPower(bear))
    }

    @Test
    fun equipCostsOneGenericManaAndAttachesToYourCreature() {
        val d = customScenario(CranialPlating, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(player, name)

        assertNotNull(activateEquip(d, player, plating, bear).error)
        assertNull(d.state.getEntity(plating)?.get<AttachedToComponent>())

        d.giveColorlessMana(player, 1)
        assertNull(activateEquip(d, player, plating, bear).error)
        d.resolveAll()
        assertAttachedTo(d, plating, bear)
    }

    @Test
    fun equipCannotTargetAnOpponentControlledCreature() {
        val d = customScenario(CranialPlating, "Grizzly Bears")
        val player = d.player1
        val opponentBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(player, name)
        d.giveColorlessMana(player, 1)

        assertNotNull(activateEquip(d, player, plating, opponentBear).error)
        assertNull(d.state.getEntity(plating)?.get<AttachedToComponent>())
    }

    @Test
    fun equipCanOnlyBeActivatedAtSorceryTiming() {
        val d = customScenario(CranialPlating, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.giveColorlessMana(player, 1)

        assertNotNull(activateEquip(d, player, plating, bear).error)
        assertEquals(1, d.manaPool(player).colorless)
        assertNull(d.state.getEntity(plating)?.get<AttachedToComponent>())
    }

    @Test
    fun blackManaAbilityRequiresTwoBlackManaAndAttachesToYourCreature() {
        val d = customScenario(CranialPlating, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(player, name)
        d.giveMana(player, Color.BLACK)
        d.giveColorlessMana(player, 1)

        assertNotNull(activateAttachAbility(d, player, plating, bear).error)
        assertNull(d.state.getEntity(plating)?.get<AttachedToComponent>())

        d.giveMana(player, Color.BLACK)
        assertNull(activateAttachAbility(d, player, plating, bear).error)
        d.resolveAll()
        assertAttachedTo(d, plating, bear)
    }

    @Test
    fun blackManaAbilityCannotTargetAnOpponentControlledCreature() {
        val d = customScenario(CranialPlating, "Grizzly Bears")
        val player = d.player1
        val opponentBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(player, name)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(activateAttachAbility(d, player, plating, opponentBear).error)
        assertNull(d.state.getEntity(plating)?.get<AttachedToComponent>())
    }

    @Test
    fun blackManaAbilityCanBeActivatedOutsideSorceryTiming() {
        val d = customScenario(CranialPlating, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(player, name)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.giveMana(player, Color.BLACK, 2)

        assertNull(activateAttachAbility(d, player, plating, bear).error)
        d.resolveAll()
        assertAttachedTo(d, plating, bear)
    }

    @Test
    fun onlyTheEquipmentControllerCanActivateItsBlackManaAbility() {
        val d = customScenario(CranialPlating, "Grizzly Bears")
        val opponent = d.player2
        val opponentBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val plating = d.putPermanentOnBattlefield(d.player1, name)
        d.giveMana(opponent, Color.BLACK, 2)

        assertNotNull(activateAttachAbility(d, opponent, plating, opponentBear).error)
        assertNull(d.state.getEntity(plating)?.get<AttachedToComponent>())
    }
}

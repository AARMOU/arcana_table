package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziMonument
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class EldraziMonumentIndependentCertificationTest {
    private val name = "Eldrazi Monument"

    private fun destroyWithMurder(d: GameTestDriver, target: EntityId) {
        val murder = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, murder, listOf(target)).error)
        d.resolveAll()
    }

    @Test
    fun castingRequiresFiveGenericMana() {
        val d = customScenario(EldraziMonument)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, name).isEmpty())

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertTrue(d.projected.hasType(card, "ARTIFACT"))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun givesEveryCreatureYouControlPlusOnePlusOneFlyingAndIndestructible() {
        val d = customScenario(EldraziMonument, "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putPermanentOnBattlefield(p, name)

        assertEquals(3, d.projected.getPower(bear))
        assertEquals(3, d.projected.getToughness(bear))
        assertTrue(d.projected.hasKeyword(bear, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        assertEquals(2, d.projected.getPower(elf))
        assertEquals(2, d.projected.getToughness(elf))
        assertTrue(d.projected.hasKeyword(elf, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(elf, Keyword.INDESTRUCTIBLE))
        assertEquals(2, d.projected.getPower(opposingBear))
        assertEquals(2, d.projected.getToughness(opposingBear))
        assertFalse(d.projected.hasKeyword(opposingBear, Keyword.FLYING))
        assertFalse(d.projected.hasKeyword(opposingBear, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun flyingPreventsAnOpponentsGroundCreatureFromBlocking() {
        val d = customScenario(EldraziMonument, "Grizzly Bears")
        val p = d.player1
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putPermanentOnBattlefield(p, name)
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertTrue(d.projected.hasKeyword(attacker, Keyword.FLYING))
        assertNull(d.declareAttackers(p, listOf(attacker), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(attacker))).error)
        assertNull(d.declareNoBlockers(d.player2).error)
    }

    @Test
    fun indestructibleSavesYourCreatureFromDestruction() {
        val d = customScenario(EldraziMonument, "Grizzly Bears", "Murder")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putPermanentOnBattlefield(p, name)

        destroyWithMurder(d, bear)

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").contains(bear))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun monumentDoesNotGiveIndestructibleToAnOpponentsCreature() {
        val d = customScenario(EldraziMonument, "Grizzly Bears", "Murder")
        val p = d.player1
        val enemyBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putPermanentOnBattlefield(p, name)

        assertFalse(d.projected.hasKeyword(enemyBear, Keyword.INDESTRUCTIBLE))
        destroyWithMurder(d, enemyBear)

        assertFalse(d.permanentsNamed(d.player2, "Grizzly Bears").contains(enemyBear))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun yourUpkeepSacrificesAYouControlCreatureAndKeepsTheMonument() {
        val d = customScenario(EldraziMonument, "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        d.putPermanentOnBattlefield(p, name)
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Llanowar Elves")

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(p, "Llanowar Elves").isNotEmpty())
        assertTrue(d.permanentsNamed(p, name).isNotEmpty())
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun whenYouCannotSacrificeACreatureYouSacrificeTheMonumentInstead() {
        val d = customScenario(EldraziMonument, "Grizzly Bears")
        val p = d.player1
        val monument = d.putPermanentOnBattlefield(p, name)
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()

        assertFalse(d.permanentsNamed(p, name).contains(monument))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(name))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").contains(opposingBear))
    }

    @Test
    fun upkeepTriggerDoesNotSacrificeUntilYourUpkeep() {
        val d = customScenario(EldraziMonument, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putPermanentOnBattlefield(p, name)

        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").contains(bear))
        assertTrue(d.permanentsNamed(p, name).isNotEmpty())

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertFalse(d.permanentsNamed(p, "Grizzly Bears").contains(bear))
        assertTrue(d.permanentsNamed(p, name).isNotEmpty())
    }
}

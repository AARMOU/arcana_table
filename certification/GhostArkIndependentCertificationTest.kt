package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GhostArk
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class GhostArkIndependentCertificationTest {
    @Test
    fun castingRequiresFourGenericManaAndEntersAsAFlyingArtifactVehicle() {
        val d = customScenario(GhostArk)
        val p = d.player1
        val ark = d.putCardInHand(p, "Ghost Ark")
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, ark).error)
        assertTrue(d.getHand(p).contains(ark))
        assertTrue(d.permanentsNamed(p, "Ghost Ark").isEmpty())

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ark).error)
        d.resolveAll()

        assertTrue(d.projected.hasType(ark, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(ark, "VEHICLE"))
        assertTrue(d.projected.hasKeyword(ark, Keyword.FLYING))
        assertFalse(d.projected.isCreature(ark))
        assertEquals(3, d.projected.getPower(ark))
        assertEquals(3, d.projected.getToughness(ark))
    }

    @Test
    fun crewTwoTapsCreaturesAndMakesGhostArkACreatureUntilEndOfTurn() {
        val d = customScenario(GhostArk, "Llanowar Elves")
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val firstElf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val secondElf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        d.removeSummoningSickness(firstElf)
        d.removeSummoningSickness(secondElf)

        assertFalse(d.projected.isCreature(ark))
        assertNull(d.submit(CrewVehicle(p, ark, listOf(firstElf, secondElf))).error)
        d.resolveAll()

        assertTrue(d.isTapped(firstElf))
        assertTrue(d.isTapped(secondElf))
        assertFalse(d.isTapped(ark))
        assertTrue(d.projected.isCreature(ark))
        assertTrue(d.projected.hasType(ark, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(ark, "VEHICLE"))
        assertTrue(d.projected.hasKeyword(ark, Keyword.FLYING))
        assertEquals(3, d.projected.getPower(ark))
        assertEquals(3, d.projected.getToughness(ark))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        assertFalse("Crew wears off at end of turn", d.projected.isCreature(ark))
    }

    @Test
    fun crewRequiresTwoTotalPowerFromCreaturesYouControl() {
        val d = customScenario(GhostArk, "Llanowar Elves", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val opposingBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(elf)
        d.removeSummoningSickness(opposingBear)

        assertNotNull(d.submit(CrewVehicle(p, ark, listOf(elf))).error)
        assertNotNull(d.submit(CrewVehicle(opponent, ark, listOf(opposingBear))).error)

        assertFalse(d.isTapped(elf))
        assertFalse(d.isTapped(opposingBear))
        assertFalse(d.projected.isCreature(ark))
    }

    @Test
    fun becomingCrewedGrantsUnearthToEachOwnArtifactCreatureInTheGraveyard() {
        val d = customScenario(GhostArk, "Myr Retriever", "Ornithopter", "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val crew = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(crew)
        val retriever = d.putCardInGraveyard(p, "Myr Retriever")
        val ornithopter = d.putCardInGraveyard(p, "Ornithopter")
        val nonartifactCreature = d.putCardInGraveyard(p, "Grizzly Bears")
        val noncreatureArtifact = d.putCardInGraveyard(p, "Sol Ring")
        val opposingRetriever = d.putCardInGraveyard(opponent, "Myr Retriever")

        assertTrue(d.abilityActions(p, retriever).isEmpty())
        assertNull(d.submit(CrewVehicle(p, ark, listOf(crew))).error)
        d.resolveAll()
        d.giveColorlessMana(p, 6)

        for (card in listOf(retriever, ornithopter)) {
            val unearth = d.abilityActions(p, card).single()
            assertNull(d.submit(unearth.action).error)
            d.resolveAll()
        }

        assertTrue(d.permanentsNamed(p, "Myr Retriever").contains(retriever))
        assertTrue(d.permanentsNamed(p, "Ornithopter").contains(ornithopter))
        assertTrue(d.projected.hasKeyword(retriever, Keyword.HASTE))
        assertTrue(d.projected.hasKeyword(ornithopter, Keyword.HASTE))
        assertTrue(d.getGraveyard(p).contains(nonartifactCreature))
        assertTrue(d.getGraveyard(p).contains(noncreatureArtifact))
        assertTrue(d.getGraveyard(opponent).contains(opposingRetriever))
        assertTrue(d.abilityActions(p, nonartifactCreature).isEmpty())
        assertTrue(d.abilityActions(p, noncreatureArtifact).isEmpty())
        assertTrue(d.abilityActions(p, opposingRetriever).isEmpty())
        assertEquals(0, d.manaPool(p).colorless)

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Myr Retriever"))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Ornithopter"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Myr Retriever"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Ornithopter"))
    }

    @Test
    fun unearthCostsThreeGenericMana() {
        val d = customScenario(GhostArk, "Myr Retriever", "Grizzly Bears")
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val crew = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(crew)
        val retriever = d.putCardInGraveyard(p, "Myr Retriever")
        assertNull(d.submit(CrewVehicle(p, ark, listOf(crew))).error)
        d.resolveAll()

        d.giveColorlessMana(p, 2)
        assertTrue(d.abilityActions(p, retriever).isEmpty())
        assertTrue(d.getGraveyard(p).contains(retriever))

        d.giveColorlessMana(p, 1)
        val unearth = d.abilityActions(p, retriever).single()
        assertNull(d.submit(unearth.action).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Myr Retriever").contains(retriever))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun unearthCannotBeActivatedOutsideSorceryTiming() {
        val d = customScenario(GhostArk, "Myr Retriever", "Grizzly Bears")
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val crew = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(crew)
        val retriever = d.putCardInGraveyard(p, "Myr Retriever")
        assertNull(d.submit(CrewVehicle(p, ark, listOf(crew))).error)
        d.resolveAll()

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.giveColorlessMana(p, 3)

        assertTrue(d.abilityActions(p, retriever).isEmpty())
        assertTrue(d.getGraveyard(p).contains(retriever))
    }

    @Test
    fun unearthGrantExpiresAtTheEndOfTurn() {
        val d = customScenario(GhostArk, "Myr Retriever", "Grizzly Bears")
        val p = d.player1
        val ark = d.putPermanentOnBattlefield(p, "Ghost Ark")
        val crew = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(crew)
        val retriever = d.putCardInGraveyard(p, "Myr Retriever")
        assertNull(d.submit(CrewVehicle(p, ark, listOf(crew))).error)
        d.resolveAll()
        d.giveColorlessMana(p, 3)
        assertEquals(1, d.abilityActions(p, retriever).size)

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)

        assertTrue(d.abilityActions(p, retriever).isEmpty())
        assertTrue(d.getGraveyard(p).contains(retriever))
    }
}

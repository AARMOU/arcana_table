package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SzarekhTheSilentKing
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SzarekhTheSilentKingScenarioTest {
    @Test
    fun attackingMillsThreeAndReturnsOneArtifactCreatureMilledThisWay() {
        val d = customScenario(SzarekhTheSilentKing, "Ornithopter", "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val king = d.putCreatureOnBattlefield(p, "Szarekh, the Silent King")
        assertTrue(d.projected.hasKeyword(king, Keyword.FLYING))
        d.removeSummoningSickness(king)
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Ornithopter")
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(king), d.player2).error)
        d.resolveAll(Choices(pick = listOf("Ornithopter")))
        assertEquals(before + 1, d.getHand(p).size)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Ornithopter"))
        assertEquals(setOf("Grizzly Bears", "Sol Ring"), d.zoneNames(p, Zone.GRAVEYARD).toSet())
    }

    @Test
    fun anUncrewedVehicleIsEligible() {
        val d = customScenario(SzarekhTheSilentKing, "Smuggler's Copter")
        val p = d.player1
        val king = d.putCreatureOnBattlefield(p, "Szarekh, the Silent King")
        d.removeSummoningSickness(king)
        d.putCardOnTopOfLibrary(p, "Smuggler's Copter")
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(king), d.player2).error)
        d.resolveAll(Choices(pick = listOf("Smuggler's Copter")))
        assertEquals(before + 1, d.getHand(p).size)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Smuggler's Copter"))
        assertEquals(2, d.zoneNames(p, Zone.GRAVEYARD).count { it == "Plains" })
    }

    @Test
    fun returningACardIsOptionalEvenWhenAnArtifactCreatureWasMilled() {
        val d = customScenario(SzarekhTheSilentKing, "Ornithopter")
        val p = d.player1
        val king = d.putCreatureOnBattlefield(p, "Szarekh, the Silent King")
        d.removeSummoningSickness(king)
        d.putCardOnTopOfLibrary(p, "Ornithopter")
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(king), d.player2).error)
        d.resolveAll()
        assertEquals(before, d.getHand(p).size)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Ornithopter"))
        assertEquals(3, d.getGraveyard(p).size)
    }

    @Test
    fun ordinaryCreaturesNoncreatureArtifactsAndOlderGraveyardCardsAreIneligible() {
        val d = customScenario(SzarekhTheSilentKing, "Ornithopter", "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val king = d.putCreatureOnBattlefield(p, "Szarekh, the Silent King")
        d.removeSummoningSickness(king)
        val older = d.putCardInGraveyard(p, "Ornithopter")
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(king), d.player2).error)
        d.resolveAll(Choices(pick = listOf("Ornithopter", "Grizzly Bears", "Sol Ring")))
        assertEquals(before, d.getHand(p).size)
        assertTrue(older in d.getGraveyard(p))
        assertEquals(4, d.getGraveyard(p).size)
    }

    @Test
    fun notAttackingDoesNotMill() {
        val d = customScenario(SzarekhTheSilentKing)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Szarekh, the Silent King")
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertTrue(d.getGraveyard(p).isEmpty())
    }
}

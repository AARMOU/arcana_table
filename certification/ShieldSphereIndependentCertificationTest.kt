package com.arcanatable.customcards

// Trusted-main, real-Argentum scenarios for Shield Sphere (#509), written from the Oracle record:
// "{0}", Artifact Creature — Wall, 0/6. Defender. Whenever this creature blocks, put a -0/-1 counter on it.
import com.arcanatable.customcards.cards.ShieldSphere
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ShieldSphereIndependentCertificationTest {
    @Test
    fun zeroManaCastEntersAsAZeroSixDefender() {
        val d = customScenario(ShieldSphere)
        val player = d.player1
        val sphere = d.putCardInHand(player, "Shield Sphere")

        assertNull(d.castSpell(player, sphere).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(sphere))
        assertEquals(0, d.projected.getPower(sphere))
        assertEquals(6, d.projected.getToughness(sphere))
        assertTrue(d.projected.hasKeyword(sphere, Keyword.DEFENDER))
    }

    @Test
    fun defenderCannotAttackAfterSummoningSicknessEnds() {
        val d = customScenario(ShieldSphere)
        val sphere = d.putCreatureOnBattlefield(d.player1, "Shield Sphere")
        d.removeSummoningSickness(sphere)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNotNull(d.declareAttackers(d.player1, listOf(sphere), d.player2).error)
        assertFalse(d.isTapped(sphere))
        assertEquals(6, d.projected.getToughness(sphere))
    }

    @Test
    fun blockingPutsOneMinusZeroMinusOneCounterOnShieldSphere() {
        val d = customScenario(ShieldSphere, "Grizzly Bears")
        val sphere = d.putCreatureOnBattlefield(d.player1, "Shield Sphere")
        val attacker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)

        assertNull(d.declareAttackers(d.player2, listOf(attacker), d.player1).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player1, mapOf(sphere to listOf(attacker))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player1).contains(sphere))
        assertEquals(0, d.projected.getPower(sphere))
        assertEquals(5, d.projected.getToughness(sphere))
    }

    @Test
    fun anUnblockedShieldSphereDoesNotGetACounter() {
        val d = customScenario(ShieldSphere, "Grizzly Bears")
        val sphere = d.putCreatureOnBattlefield(d.player1, "Shield Sphere")
        val attacker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)

        assertNull(d.declareAttackers(d.player2, listOf(attacker), d.player1).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player1, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player1).contains(sphere))
        assertEquals(0, d.projected.getPower(sphere))
        assertEquals(6, d.projected.getToughness(sphere))
    }
}

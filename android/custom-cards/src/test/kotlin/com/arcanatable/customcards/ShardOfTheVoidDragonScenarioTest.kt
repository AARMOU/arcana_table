package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ShardOfTheVoidDragon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class ShardOfTheVoidDragonScenarioTest {
    private fun game() = customScenario(ShardOfTheVoidDragon, "Sol Ring", "Ornithopter", "Disenchant", "Swords to Plowshares", "Unsummon", "Grizzly Bears", "Shatterstorm")

    @Test fun attackMakesOpponentChooseANonlandAndLeavesYourPermanentsAlone() {
        val d = game(); val p = d.player1; val opp = d.player2
        val dragon = d.putCreatureOnBattlefield(p, ShardOfTheVoidDragon.name)
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.putPermanentOnBattlefield(opp, "Sol Ring")
        d.putCreatureOnBattlefield(opp, "Grizzly Bears")
        d.putLandOnBattlefield(opp, "Plains")
        d.removeSummoningSickness(dragon)
        assertTrue(d.projected.hasKeyword(dragon, Keyword.FLYING))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(dragon), opp).error)
        d.resolveAll(Choices(pick = listOf("Sol Ring")))
        assertTrue("Sol Ring" in d.zoneNames(opp, Zone.GRAVEYARD))
        assertNotNull(d.findPermanent(p, "Sol Ring"))
        assertNotNull(d.findPermanent(opp, "Plains"))
        assertNotNull(d.findPermanent(opp, "Grizzly Bears"))
        assertEquals(9, d.projected.getPower(dragon))
    }
    @Test fun opponentWithOnlyLandsSacrificesNothing() {
        val d = game(); val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, ShardOfTheVoidDragon.name)
        d.putLandOnBattlefield(d.player2, "Plains")
        d.removeSummoningSickness(dragon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(dragon), d.player2).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player2, "Plains"))
        assertEquals(7, d.projected.getPower(dragon))
    }
    @Test fun destroyingEitherPlayersArtifactAddsTwoCounters() {
        for (own in listOf(true, false)) {
            val d = game(); val p = d.player1
            val dragon = d.putCreatureOnBattlefield(p, ShardOfTheVoidDragon.name)
            val artifact = d.putPermanentOnBattlefield(if (own) p else d.player2, "Sol Ring")
            val removal = d.putCardInHand(p, "Disenchant")
            d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 1)
            assertNull(d.castSpell(p, removal, listOf(artifact)).error); d.resolveAll()
            assertEquals(9, d.projected.getPower(dragon)); assertEquals(9, d.projected.getToughness(dragon))
        }
    }
    @Test fun exilingArtifactCreatureCountsButNonartifactCreatureDoesNot() {
        for (artifact in listOf(true, false)) {
            val d = game(); val p = d.player1
            val dragon = d.putCreatureOnBattlefield(p, ShardOfTheVoidDragon.name)
            val creature = d.putCreatureOnBattlefield(d.player2, if (artifact) "Ornithopter" else "Grizzly Bears")
            val removal = d.putCardInHand(p, "Swords to Plowshares")
            d.giveMana(p, Color.WHITE)
            assertNull(d.castSpell(p, removal, listOf(creature)).error); d.resolveAll()
            assertEquals(if (artifact) 9 else 7, d.projected.getPower(dragon))
        }
    }
    @Test fun returningAnArtifactToHandDoesNotAddCounters() {
        val d = game(); val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, ShardOfTheVoidDragon.name)
        val creature = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val bounce = d.putCardInHand(p, "Unsummon")
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, bounce, listOf(creature)).error); d.resolveAll()
        assertEquals(7, d.projected.getPower(dragon))
        assertTrue("Ornithopter" in d.zoneNames(d.player2, Zone.HAND))
    }
    @Test fun simultaneousArtifactDeathsTriggerOncePerArtifactIncludingTokens() {
        val d = game(); val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, ShardOfTheVoidDragon.name)
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.putToken(d.player2, "Ornithopter")
        val sweep = d.putCardInHand(p, "Shatterstorm")
        d.giveMana(p, Color.RED, 2); d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, sweep).error); d.resolveAll()
        assertEquals(11, d.projected.getPower(dragon)); assertEquals(11, d.projected.getToughness(dragon))
    }
    @Test fun attackMakesBothOpponentsSacrificeEvenWhenOnlyOneIsAttacked() {
        val d = game()
        val seats = d.initMultiplayer(List(3) { com.wingedsheep.sdk.model.Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val dragon = d.putCreatureOnBattlefield(seats[0], ShardOfTheVoidDragon.name)
        d.putPermanentOnBattlefield(seats[0], "Sol Ring")
        d.putPermanentOnBattlefield(seats[1], "Sol Ring")
        d.putCreatureOnBattlefield(seats[2], "Grizzly Bears")
        d.removeSummoningSickness(dragon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(seats[0], listOf(dragon), seats[1]).error)
        d.resolveAll()
        assertTrue("Sol Ring" in d.zoneNames(seats[1], Zone.GRAVEYARD))
        assertTrue("Grizzly Bears" in d.zoneNames(seats[2], Zone.GRAVEYARD))
        assertNotNull(d.findPermanent(seats[0], "Sol Ring"))
        assertEquals(9, d.projected.getPower(dragon))
    }

}

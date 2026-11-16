package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KeeperOfSecrets
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class KeeperOfSecretsIndependentCertificationTest {
    private val name = "Keeper of Secrets"

    @Test
    fun castsForFiveGenericAndRedAsASixFourDemonWithFirstStrikeAndHaste() {
        val d = customScenario(KeeperOfSecrets)
        val player = d.player1
        val keeper = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 5)
        d.giveMana(player, Color.RED)

        assertNull(d.castSpell(player, keeper).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(keeper))
        assertEquals(6, d.projected.getPower(keeper))
        assertEquals(4, d.projected.getToughness(keeper))
        assertTrue(d.projected.hasColor(keeper, Color.RED))
        assertTrue(d.projected.hasSubtype(keeper, "DEMON"))
        assertTrue(d.projected.hasKeyword(keeper, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(keeper, Keyword.HASTE))
    }

    @Test
    fun sixGenericManaCannotReplaceTheRequiredRedMana() {
        val d = customScenario(KeeperOfSecrets)
        val player = d.player1
        val keeper = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)

        assertNotNull(d.castSpell(player, keeper).error)

        assertTrue(d.getHand(player).contains(keeper))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun fourGenericAndRedManaDoNotPayTheFiveGenericCost() {
        val d = customScenario(KeeperOfSecrets)
        val player = d.player1
        val keeper = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.RED)

        assertNotNull(d.castSpell(player, keeper).error)

        assertTrue(d.getHand(player).contains(keeper))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun hasteAllowsItToAttackTheTurnItEnters() {
        val d = customScenario(KeeperOfSecrets)
        val player = d.player1
        val opponent = d.player2
        val keeper = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 5)
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, keeper).error)
        d.resolveAll()

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(keeper), opponent).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(14, d.getLifeTotal(opponent))
        assertTrue(d.getPermanents(player).contains(keeper))
    }

    @Test
    fun firstStrikeKillsAColossalDreadmawBeforeItDealsCombatDamage() {
        val d = customScenario(KeeperOfSecrets, "Colossal Dreadmaw")
        val player = d.player1
        val opponent = d.player2
        val keeper = d.putCreatureOnBattlefield(player, name)
        val dreadmaw = d.putCreatureOnBattlefield(opponent, "Colossal Dreadmaw")
        d.removeSummoningSickness(dreadmaw)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)

        assertNull(d.declareAttackers(opponent, listOf(dreadmaw), player).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(player, mapOf(keeper to listOf(dreadmaw))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(keeper))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Colossal Dreadmaw"))
        assertEquals(20, d.getLifeTotal(player))
    }

    @Test
    fun flashbackFromTheGraveyardDealsDamageEqualToTheSpellsManaValueToAnOpponent() {
        val d = customScenario(KeeperOfSecrets, "Think Twice")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, name)
        val thinkTwice = d.putCardInGraveyard(player, "Think Twice")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, thinkTwice).error)
        assertEquals(2, d.stackSize)
        d.resolveAll(Choices(targets = listOf(opponent)))

        assertEquals(18, d.getLifeTotal(opponent))
        assertEquals(20, d.getLifeTotal(player))
        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Think Twice"))
    }

    @Test
    fun castingTheSameSpellFromHandDoesNotTriggerSymphonyOfPain() {
        val d = customScenario(KeeperOfSecrets, "Think Twice")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, name)
        val thinkTwice = d.putCardInHand(player, "Think Twice")
        d.giveColorlessMana(player, 1)
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, thinkTwice).error)
        assertEquals(1, d.stackSize)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(opponent))
        assertEquals(20, d.getLifeTotal(player))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Think Twice"))
    }

    @Test
    fun anOpponentsFlashbackSpellDoesNotTriggerYourKeeper() {
        val d = customScenario(KeeperOfSecrets, "Think Twice")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, name)
        val thinkTwice = d.putCardInGraveyard(opponent, "Think Twice")
        d.giveColorlessMana(opponent, 2)
        d.giveMana(opponent, Color.BLUE)
        assertNull(d.passPriority(player).error)

        assertNull(d.castSpell(opponent, thinkTwice).error)
        assertEquals(1, d.stackSize)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(player))
        assertEquals(20, d.getLifeTotal(opponent))
    }
}

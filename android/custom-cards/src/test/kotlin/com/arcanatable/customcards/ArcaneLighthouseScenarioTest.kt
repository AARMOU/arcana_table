package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArcaneLighthouse
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

private val ShroudTestCreature = card("Shroud Test Creature") {
    manaCost = "{1}"
    typeLine = "Creature — Spirit"
    power = 1
    toughness = 1
    oracleText = "Shroud"
    keywords(Keyword.SHROUD)
    metadata { rarity = Rarity.COMMON; collectorNumber = "1" }
}

/**
 * Arcane Lighthouse — "{1}, {T}: Until end of turn, creatures your opponents control lose hexproof
 * and shroud and can't have hexproof or shroud." On engine patch 0043; the three 2014-11-07 rulings
 * are each a case below.
 */
class ArcaneLighthouseScenarioTest {

    private fun scenario(): GameTestDriver =
        customScenario(ArcaneLighthouse, "Troll Ascetic", "Gladecover Scout", "Heroic Intervention", "Lightning Bolt")
            .apply { registerCard(ShroudTestCreature) }

    private fun GameTestDriver.has(id: EntityId, k: Keyword) = state.projectedState.hasKeyword(id, k)

    private fun GameTestDriver.light(player: EntityId): EntityId {
        val lighthouse = putPermanentOnBattlefield(player, "Arcane Lighthouse")
        giveColorlessMana(player, 1)
        assertNull(activate(player, lighthouse, index = 1).error)
        resolveAll()
        return lighthouse
    }

    private fun GameTestDriver.canBolt(player: EntityId, target: EntityId): Boolean {
        val bolt = putCardInHand(player, "Lightning Bolt")
        giveMana(player, Color.RED, 1)
        val ok = castSpell(player, bolt, listOf(target)).error == null
        if (ok) resolveAll()
        return ok
    }

    @Test
    fun opponentsHexproofAndShroudCreaturesBecomeTargetable() {
        val d = scenario()
        val me = d.player1; val opp = d.player2
        val troll = d.putCreatureOnBattlefield(opp, "Troll Ascetic")
        val spirit = d.putCreatureOnBattlefield(opp, "Shroud Test Creature")
        assertFalse("hexproof stops an opponent's Bolt first", d.canBolt(me, troll))
        d.light(me)
        assertFalse(d.has(troll, Keyword.HEXPROOF))
        assertFalse(d.has(spirit, Keyword.SHROUD))
        assertTrue("now targetable", d.canBolt(me, spirit))
        assertFalse("the 1/1 died to Bolt", spirit in d.getCreatures(opp))
    }

    @Test
    fun yourOwnCreaturesKeepHexproof() {
        val d = scenario()
        val mine = d.putCreatureOnBattlefield(d.player1, "Gladecover Scout")
        d.light(d.player1)
        assertTrue("only creatures your opponents control", d.has(mine, Keyword.HEXPROOF))
    }

    @Test
    fun aLaterGrantDoesNotGiveHexproofBackThisTurn() {
        val d = scenario()
        val me = d.player1; val opp = d.player2
        val scout = d.putCreatureOnBattlefield(opp, "Gladecover Scout")
        d.light(me)
        // The opponent responds afterwards with Heroic Intervention (later timestamp).
        d.passPriority(me)
        val hi = d.putCardInHand(opp, "Heroic Intervention")
        d.giveMana(opp, Color.GREEN, 1); d.giveColorlessMana(opp, 1)
        assertNull(d.castSpell(opp, hi).error)
        d.resolveAll()
        assertFalse("can't have hexproof (ruling 2014-11-07)", d.has(scout, Keyword.HEXPROOF))
        assertTrue("the rest of the spell still applies", d.has(scout, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun aCreatureThatArrivesLaterIsNotAffected() {
        val d = scenario()
        d.light(d.player1)
        val late = d.putCreatureOnBattlefield(d.player2, "Troll Ascetic")
        assertTrue("locked to creatures there as it resolved (ruling)", d.has(late, Keyword.HEXPROOF))
    }

    @Test
    fun staticHexproofReturnsWhenTheTurnEnds() {
        val d = scenario()
        val troll = d.putCreatureOnBattlefield(d.player2, "Troll Ascetic")
        d.light(d.player1)
        assertFalse(d.has(troll, Keyword.HEXPROOF))
        d.passPriorityUntil(Step.UPKEEP, d.player2)
        assertTrue("until end of turn only", d.has(troll, Keyword.HEXPROOF))
    }

    @Test
    fun firstAbilityTapsForColorlessAndTheSecondNeedsItsMana() {
        val d = scenario()
        val p = d.player1
        for (land in d.getLands(p)) d.tapPermanent(land)
        val lighthouse = d.putPermanentOnBattlefield(p, "Arcane Lighthouse")
        assertNotNull("{1} is part of the cost", d.activate(p, lighthouse, index = 1).error)
        assertFalse(d.isTapped(lighthouse))
        assertNull(d.activate(p, lighthouse, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun fourPlayerEveryOpponentsCreaturesAreAffected() {
        val d = CardPool.driver("Troll Ascetic", "Gladecover Scout").apply {
            registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(ArcaneLighthouse))))
        }
        val seats = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val me = seats[0]
        val mine = d.putCreatureOnBattlefield(me, "Gladecover Scout")
        val theirs = seats.drop(1).map { d.putCreatureOnBattlefield(it, "Troll Ascetic") }
        d.light(me)
        theirs.forEach { assertFalse("every opponent's creature", d.has(it, Keyword.HEXPROOF)) }
        assertTrue(d.has(mine, Keyword.HEXPROOF))
    }
}

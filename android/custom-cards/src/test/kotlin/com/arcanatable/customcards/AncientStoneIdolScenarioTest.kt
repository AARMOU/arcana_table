package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AncientStoneIdol
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AncientStoneIdolScenarioTest {
    @Test
    fun flashAllowsCastingDuringAnOpponentsTurn() {
        val d = customScenario(AncientStoneIdol, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        val bear = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, bear).error)
        d.resolveAll()
        assertNull(d.passPriority(opponent).error)

        val idol = d.putCardInHand(p, "Ancient Stone Idol")
        d.giveColorlessMana(p, 10)
        assertNull(d.castSpell(p, idol).error)
        d.resolveAll()

        val permanent = d.findPermanent(p, "Ancient Stone Idol")!!
        assertEquals(12, d.projected.getPower(permanent))
        assertEquals(12, d.projected.getToughness(permanent))
        assertTrue(d.projected.isCreature(permanent))
        assertTrue(d.projected.hasType(permanent, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(permanent, "Golem"))
        assertTrue(d.projected.hasKeyword(permanent, Keyword.TRAMPLE))
    }

    @Test
    fun eachAttackingCreatureReducesTheGenericCostByOne() {
        val d = customScenario(AncientStoneIdol, "Grizzly Bears")
        val p = d.player1
        val attackers = List(2) { d.putCreatureOnBattlefield(p, "Grizzly Bears") }
        attackers.forEach(d::removeSummoningSickness)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)
        assertNull(d.declareAttackers(p, attackers, d.player2).error)

        val idol = d.putCardInHand(p, "Ancient Stone Idol")
        d.giveColorlessMana(p, 8)
        assertNull(d.castSpell(p, idol).error)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun noAttackersMeansTheFullGenericCostIsRequired() {
        val d = customScenario(AncientStoneIdol)
        val p = d.player1
        val idol = d.putCardInHand(p, "Ancient Stone Idol")
        d.giveColorlessMana(p, 9)

        assertNotNull(d.castSpell(p, idol).error)
        assertEquals(9, d.manaPool(p).colorless)
        assertTrue(d.getHand(p).contains(idol))
    }

    @Test
    fun dyingCreatesTheExactColorlessArtifactConstructWithTrample() {
        val d = customScenario(AncientStoneIdol, "Wrath of God")
        val p = d.player1
        val idol = d.putCreatureOnBattlefield(p, "Ancient Stone Idol")
        val wrath = d.putCardInHand(p, "Wrath of God")
        d.giveMana(p, Color.WHITE, 2)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, wrath).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(p).contains(idol))
        val token = d.getCreatures(p).single { d.state.getEntity(it)!!.has<TokenComponent>() }
        assertEquals(6, d.projected.getPower(token))
        assertEquals(12, d.projected.getToughness(token))
        assertTrue(d.projected.hasType(token, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(token, "Construct"))
        assertTrue(Color.values().none { d.projected.hasColor(token, it) })
        assertTrue(d.projected.hasKeyword(token, Keyword.TRAMPLE))
    }

    @Test
    fun exilingTheIdolDoesNotTriggerItsDiesAbility() {
        val d = customScenario(AncientStoneIdol, "Swords to Plowshares")
        val p = d.player1
        val opponent = d.player2
        val idol = d.putCreatureOnBattlefield(p, "Ancient Stone Idol")
        val swords = d.putCardInHand(opponent, "Swords to Plowshares")
        d.giveMana(opponent, Color.WHITE)
        assertNull(d.passPriority(p).error)
        assertNull(d.castSpell(opponent, swords, listOf(idol)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Ancient Stone Idol"))
        assertTrue(d.getCreatures(p).isEmpty())
    }
}

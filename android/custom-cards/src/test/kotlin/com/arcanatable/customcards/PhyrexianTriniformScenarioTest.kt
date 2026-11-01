package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PhyrexianTriniform
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.MustAttackPlayerThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class PhyrexianTriniformScenarioTest {
    @Test
    fun createsThreeColorlessArtifactGolemsWhenItDies() {
        val d = customScenario(PhyrexianTriniform, "Murder")
        val p = d.player1
        val triniform = d.putCreatureOnBattlefield(p, "Phyrexian Triniform")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, murder, listOf(triniform)).error)
        d.resolveAll()

        val tokens = d.permanentsNamed(p, "Phyrexian Golem")
        assertEquals(3, tokens.size)
        tokens.forEach { token ->
            assertTrue(d.state.getEntity(token)!!.has<TokenComponent>())
            val card = d.state.getEntity(token)!!.get<CardComponent>()!!
            assertTrue(card.typeLine.isArtifactCreature)
            assertEquals(setOf("Golem", "Phyrexian"), card.typeLine.subtypes.map { it.value }.toSet())
            assertTrue(card.colors.isEmpty())
            assertEquals(3, card.baseStats!!.basePower)
            assertEquals(3, card.baseStats!!.baseToughness)
        }
    }

    @Test
    fun anotherCreaturesDeathDoesNotCreateGolems() {
        val d = customScenario(PhyrexianTriniform, "Murder", "Grizzly Bears")
        val p = d.player1
        val triniform = d.putCreatureOnBattlefield(p, "Phyrexian Triniform")
        val bears = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, murder, listOf(bears)).error)
        d.resolveAll()

        assertEquals(listOf(triniform), d.permanentsNamed(p, "Phyrexian Triniform"))
        assertTrue(d.permanentsNamed(p, "Phyrexian Golem").isEmpty())
    }

    @Test
    fun encoreCreatesHastyAttackerForEachOpponentAndSacrificesThemAtEndStep() {
        val d = customScenario(PhyrexianTriniform)
        val players = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        val p = players.first()
        val opponents = players.drop(1).toSet()
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, p)
        val card = d.putCardInGraveyard(p, "Phyrexian Triniform")
        d.giveColorlessMana(p, 12)

        val action = d.legalActions(p).mapNotNull { it.action as? ActivateAbility }
            .single { it.sourceId == card }
        d.submitSuccess(action)
        assertEquals(listOf("Phyrexian Triniform"), d.zoneNames(p, Zone.EXILE))
        d.resolveAll()

        val tokens = d.permanentsNamed(p, "Phyrexian Triniform")
            .filter { d.state.getEntity(it)?.has<TokenComponent>() == true }
        assertEquals(opponents.size, tokens.size)
        assertTrue(tokens.all { d.projected.hasKeyword(it, com.wingedsheep.sdk.core.Keyword.HASTE) })
        val attackers = tokens.associateWith { token ->
            d.state.getEntity(token)!!.get<MustAttackPlayerThisTurnComponent>()!!.defenderIds.single()
        }
        assertEquals(opponents, attackers.values.toSet())
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, p)
        assertNull(d.declareAttackers(p, attackers).error)
        val defenders = tokens.map { token ->
            d.state.getEntity(token)!!.get<AttackingComponent>()!!.defenderId
        }.toSet()
        assertEquals(opponents, defenders)

        d.passPriorityUntil(Step.END, p)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Phyrexian Triniform")
            .none { d.state.getEntity(it)?.has<TokenComponent>() == true })
        assertEquals(listOf("Phyrexian Triniform"), d.zoneNames(p, Zone.EXILE))
    }

    @Test
    fun encoreRequiresGraveyardTwelveManaAndSorceryTiming() {
        val d = customScenario(PhyrexianTriniform)
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Phyrexian Triniform")
        fun encoreActions() = d.legalActions(p).mapNotNull { it.action as? ActivateAbility }
            .filter { it.sourceId == card }

        d.giveColorlessMana(p, 11)
        assertTrue("Encore requires all twelve mana", encoreActions().isEmpty())
        d.giveColorlessMana(p, 1)
        assertEquals(1, encoreActions().size)

        d.passPriorityUntil(Step.END, p)
        assertTrue("Encore is only available at sorcery timing", encoreActions().isEmpty())
    }

    @Test
    fun encoreIsUnavailableFromTheBattlefield() {
        val d = customScenario(PhyrexianTriniform)
        val p = d.player1
        val permanent = d.putCreatureOnBattlefield(p, "Phyrexian Triniform")
        d.giveColorlessMana(p, 12)

        assertTrue(d.legalActions(p).mapNotNull { it.action as? ActivateAbility }
            .none { it.sourceId == permanent })
    }
}

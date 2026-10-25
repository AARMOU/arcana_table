package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Psychomancer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.RedirectZoneChange
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*
import org.junit.Test

private const val DESTROY_ARTIFACTS = "Psychomancer Test — Destroy Artifacts"
private const val EXILE_ARTIFACT = "Psychomancer Test — Exile Artifact"
private const val RETURN_ARTIFACT = "Psychomancer Test — Return Artifact"
private const val CREATE_TREASURE = "Psychomancer Test — Create Treasure"
private const val ARTIFACT_SPELL = "Psychomancer Test — Artifact Spell"
private const val COUNTER_SPELL = "Psychomancer Test — Counter Spell"
private const val REDIRECT_TO_EXILE = "Psychomancer Test — Redirect to Exile"
private const val REDIRECT_TO_HAND = "Psychomancer Test — Redirect to Hand"

private val DestroyArtifactsTest = card(DESTROY_ARTIFACTS) {
    manaCost = "{0}"
    typeLine = "Sorcery"
    spell { effect = Effects.DestroyAll(GameObjectFilter.Artifact) }
}

private val ExileArtifactTest = card(EXILE_ARTIFACT) {
    manaCost = "{0}"
    typeLine = "Sorcery"
    spell {
        val artifact = target(TargetFilter(GameObjectFilter.Artifact))
        effect = Effects.Exile(artifact)
    }
}

private val ReturnArtifactTest = card(RETURN_ARTIFACT) {
    manaCost = "{0}"
    typeLine = "Sorcery"
    spell {
        val artifact = target(TargetFilter(GameObjectFilter.Artifact))
        effect = Effects.ReturnToHand(artifact)
    }
}

private val CreateTreasureTest = card(CREATE_TREASURE) {
    manaCost = "{0}"
    typeLine = "Sorcery"
    spell { effect = Effects.CreateTreasure(1) }
}

private val ArtifactSpellTest = card(ARTIFACT_SPELL) {
    manaCost = "{0}"
    typeLine = "Artifact"
}

private val CounterSpellTest = card(COUNTER_SPELL) {
    manaCost = "{0}"
    typeLine = "Instant"
    spell {
        target(TargetFilter.SpellOnStack)
        effect = Effects.CounterSpell()
    }
}

private val RedirectToExileTest = card(REDIRECT_TO_EXILE) {
    manaCost = "{0}"
    typeLine = "Artifact"
    replacementEffect(
        RedirectZoneChange(
            newDestination = Zone.EXILE,
            appliesTo = EventPattern.ZoneChangeEvent(to = Zone.GRAVEYARD),
            selfOnly = true,
        )
    )
}

private val RedirectToHandTest = card(REDIRECT_TO_HAND) {
    manaCost = "{0}"
    typeLine = "Artifact"
    replacementEffect(
        RedirectZoneChange(
            newDestination = Zone.HAND,
            appliesTo = EventPattern.ZoneChangeEvent(to = Zone.GRAVEYARD),
            selfOnly = true,
        )
    )
}

private fun GameTestDriver.registerPsychomancerScenarioCards() {
    registerCards(
        listOf(
            DestroyArtifactsTest,
            ExileArtifactTest,
            ReturnArtifactTest,
            CreateTreasureTest,
            ArtifactSpellTest,
            CounterSpellTest,
            RedirectToExileTest,
            RedirectToHandTest,
        )
    )
}

class PsychomancerScenarioTest {
    @Test
    fun castsForOneBlackAndOneGenericAndHasOracleStatsTypeAndFlying() {
        val d = customScenario(Psychomancer)
        val p = d.player1
        val psychomancer = d.putCardInHand(p, "Psychomancer")

        d.giveMana(p, Color.BLACK)
        assertNotNull(d.castSpell(p, psychomancer).error)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, psychomancer).error)
        d.resolveAll()

        assertEquals("Artifact Creature — Necron Wizard", d.cardRegistry.getCard("Psychomancer")!!.typeLine.toString())
        assertEquals(1, d.projected.getPower(psychomancer))
        assertEquals(1, d.projected.getToughness(psychomancer))
        assertTrue(d.projected.hasKeyword(psychomancer, Keyword.FLYING))
    }

    @Test
    fun simultaneousLeavesTriggerOncePerControlledNontokenArtifactAndResolveAfterSourceLeaves() {
        val d = customScenario(Psychomancer, "Sol Ring").apply { registerPsychomancerScenarioCards() }
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Psychomancer")
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.putPermanentOnBattlefield(opponent, "Sol Ring")

        val treasureMaker = d.putCardInHand(p, CREATE_TREASURE)
        assertNull(d.castSpell(p, treasureMaker).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Treasure").size)

        val destroy = d.putCardInHand(p, DESTROY_ARTIFACTS)
        assertNull(d.castSpell(p, destroy).error)
        d.bothPass()
        assertEquals(2, d.state.stack.size)
        d.resolveAll(Choices(targets = listOf(opponent)))

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).containsAll(listOf("Psychomancer", "Sol Ring")))
        assertEquals(18, d.getLifeTotal(opponent))
        assertEquals(22, d.getLifeTotal(p))
    }

    @Test
    fun directExileTriggersForAnotherArtifactAndPsychomancerItself() {
        val d = customScenario(Psychomancer, "Sol Ring").apply { registerPsychomancerScenarioCards() }
        val p = d.player1
        val opponent = d.player2
        val psychomancer = d.putCreatureOnBattlefield(p, "Psychomancer")
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")

        val exileRing = d.putCardInHand(p, EXILE_ARTIFACT)
        assertNull(d.castSpell(p, exileRing, listOf(ring)).error)
        d.resolveAll(Choices(targets = listOf(opponent)))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Sol Ring"))
        assertEquals(19, d.getLifeTotal(opponent))
        assertEquals(21, d.getLifeTotal(p))

        val exilePsychomancer = d.putCardInHand(p, EXILE_ARTIFACT)
        assertNull(d.castSpell(p, exilePsychomancer, listOf(psychomancer)).error)
        d.bothPass()
        assertEquals(1, d.state.stack.size)
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Psychomancer"))
        d.resolveAll(Choices(targets = listOf(opponent)))

        assertEquals(18, d.getLifeTotal(opponent))
        assertEquals(22, d.getLifeTotal(p))
    }

    @Test
    fun bounceAndArtifactSpellCounteredFromStackDoNotTrigger() {
        val d = customScenario(Psychomancer, "Sol Ring").apply { registerPsychomancerScenarioCards() }
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Psychomancer")
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")

        val bounce = d.putCardInHand(p, RETURN_ARTIFACT)
        assertNull(d.castSpell(p, bounce, listOf(ring)).error)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertEquals(20, d.getLifeTotal(opponent))
        assertEquals(20, d.getLifeTotal(p))

        val artifactSpell = d.putCardInHand(p, ARTIFACT_SPELL)
        assertNull(d.castSpell(p, artifactSpell).error)
        val spellOnStack = d.getTopOfStack()!!
        val counter = d.putCardInHand(opponent, COUNTER_SPELL)
        assertNull(
            d.castSpellWithTargets(
                opponent,
                counter,
                listOf(ChosenTarget.Spell(spellOnStack)),
            ).error
        )
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains(ARTIFACT_SPELL))
        assertEquals(20, d.getLifeTotal(opponent))
        assertEquals(20, d.getLifeTotal(p))
    }

    @Test
    fun replacementDestinationDeterminesWhetherTheArtifactTriggers() {
        val d = customScenario(Psychomancer).apply { registerPsychomancerScenarioCards() }
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Psychomancer")
        d.putPermanentOnBattlefield(p, REDIRECT_TO_EXILE)
        d.putPermanentOnBattlefield(p, REDIRECT_TO_HAND)

        val destroy = d.putCardInHand(p, DESTROY_ARTIFACTS)
        assertNull(d.castSpell(p, destroy).error)
        d.bothPass()
        assertEquals(2, d.state.stack.size)
        d.resolveAll(Choices(targets = listOf(opponent)))

        assertTrue(d.zoneNames(p, Zone.EXILE).contains(REDIRECT_TO_EXILE))
        assertTrue(d.zoneNames(p, Zone.HAND).contains(REDIRECT_TO_HAND))
        assertEquals(18, d.getLifeTotal(opponent))
        assertEquals(22, d.getLifeTotal(p))
    }

    @Test
    fun opponentArtifactExiledFromBattlefieldDoesNotTrigger() {
        val d = customScenario(Psychomancer, "Sol Ring").apply { registerPsychomancerScenarioCards() }
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Psychomancer")
        val enemyRing = d.putPermanentOnBattlefield(opponent, "Sol Ring")

        val exile = d.putCardInHand(p, EXILE_ARTIFACT)
        assertNull(d.castSpell(p, exile, listOf(enemyRing)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(opponent, Zone.EXILE).contains("Sol Ring"))
        assertEquals(20, d.getLifeTotal(opponent))
        assertEquals(20, d.getLifeTotal(p))
    }
}

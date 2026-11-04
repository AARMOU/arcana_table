package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Biotransference
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class BiotransferenceIndependentCertificationTest {
    private fun scenarioWithBiotransference(vararg libraryCards: String): GameTestDriver =
        customScenario(Biotransference, *libraryCards).apply {
            putPermanentOnBattlefield(player1, "Biotransference")
        }

    private fun assertNecronWarriors(d: GameTestDriver, player: EntityId, expectedCount: Int) {
        val warriors = d.permanentsNamed(player, "Necron Warrior")
        assertEquals(expectedCount, warriors.size)
        warriors.forEach { warrior ->
            assertTrue(d.projected.hasType(warrior, "ARTIFACT"))
            assertTrue(d.projected.hasType(warrior, "CREATURE"))
            assertTrue(d.projected.hasSubtype(warrior, "NECRON"))
            assertTrue(d.projected.hasSubtype(warrior, "WARRIOR"))
            assertTrue(d.projected.hasColor(warrior, Color.BLACK))
            assertEquals(2, d.projected.getPower(warrior))
            assertEquals(2, d.projected.getToughness(warrior))
        }
    }

    @Test
    fun castingBiotransferencePaysTwoGenericAndTwoBlackMana() {
        val d = customScenario(Biotransference)
        val p = d.player1
        val card = d.putCardInHand(p, "Biotransference")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Biotransference").contains(card))
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(20, d.getLifeTotal(p))
        assertNecronWarriors(d, p, 0)
    }

    @Test
    fun threeManaCannotPayBiotransferencesFourManaCost() {
        val d = customScenario(Biotransference)
        val p = d.player1
        val card = d.putCardInHand(p, "Biotransference")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, "Biotransference").isEmpty())
        assertEquals(20, d.getLifeTotal(p))
    }

    @Test
    fun fourColorlessManaCannotPayBiotransferencesBlackSymbols() {
        val d = customScenario(Biotransference)
        val p = d.player1
        val card = d.putCardInHand(p, "Biotransference")
        d.giveColorlessMana(p, 4)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, "Biotransference").isEmpty())
        assertEquals(20, d.getLifeTotal(p))
    }

    @Test
    fun onlyYourBattlefieldCreaturesAndCreatureSpellsAreArtifacts() {
        val d = scenarioWithBiotransference("Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val ownCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        assertTrue(d.projected.hasType(ownCreature, "CREATURE"))
        assertTrue(d.projected.hasType(ownCreature, "ARTIFACT"))
        assertTrue(d.projected.hasType(opposingCreature, "CREATURE"))
        assertFalse(d.projected.hasType(opposingCreature, "ARTIFACT"))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val opposingSpell = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, opposingSpell).error)
        val spellOnStack = d.state.stack.single { d.getCardName(it) == "Grizzly Bears" }

        assertTrue(d.state.getEntity(spellOnStack)!!.get<CardComponent>()!!.typeLine.isCreature)
        assertFalse(d.projected.crossZoneGrantedCardTypes(spellOnStack).contains("ARTIFACT"))
        d.resolveAll()

        val opposingCreatures = d.permanentsNamed(opponent, "Grizzly Bears")
        assertEquals(2, opposingCreatures.size)
        assertTrue(opposingCreatures.none { d.projected.hasType(it, "ARTIFACT") })
        assertEquals(20, d.getLifeTotal(p))
        assertNecronWarriors(d, p, 0)
    }

    @Test
    fun yourCreatureCardsInEveryNonbattlefieldZoneAreArtifacts() {
        val d = scenarioWithBiotransference("Grizzly Bears")
        val ownCards = listOf(
            d.putCardInHand(d.player1, "Grizzly Bears"),
            d.putCardInGraveyard(d.player1, "Grizzly Bears"),
            d.putCardInExile(d.player1, "Grizzly Bears"),
            d.putCardOnTopOfLibrary(d.player1, "Grizzly Bears"),
        )
        val opponentsCards = listOf(
            d.putCardInHand(d.player2, "Grizzly Bears"),
            d.putCardInGraveyard(d.player2, "Grizzly Bears"),
            d.putCardInExile(d.player2, "Grizzly Bears"),
            d.putCardOnTopOfLibrary(d.player2, "Grizzly Bears"),
        )

        ownCards.forEach { card ->
            assertTrue(d.state.getEntity(card)!!.get<CardComponent>()!!.typeLine.isCreature)
            assertTrue(d.projected.crossZoneGrantedCardTypes(card).contains("ARTIFACT"))
        }
        opponentsCards.forEach { card ->
            assertTrue(d.state.getEntity(card)!!.get<CardComponent>()!!.typeLine.isCreature)
            assertFalse(d.projected.crossZoneGrantedCardTypes(card).contains("ARTIFACT"))
        }
        assertTrue(d.zoneNames(d.player1, Zone.HAND).contains("Grizzly Bears"))
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.zoneNames(d.player1, Zone.EXILE).contains("Grizzly Bears"))
        assertTrue(d.zoneNames(d.player1, Zone.LIBRARY).contains("Grizzly Bears"))
    }

    @Test
    fun yourCreatureSpellIsAnArtifactAndTriggersWhenCast() {
        val d = scenarioWithBiotransference("Grizzly Bears")
        val p = d.player1
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, bear).error)
        val spellOnStack = d.state.stack.single { d.getCardName(it) == "Grizzly Bears" }
        assertTrue(d.state.getEntity(spellOnStack)!!.get<CardComponent>()!!.typeLine.isCreature)
        assertTrue(d.projected.crossZoneGrantedCardTypes(spellOnStack).contains("ARTIFACT"))
        assertEquals(20, d.getLifeTotal(p))
        d.resolveAll()

        assertEquals(19, d.getLifeTotal(p))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").contains(bear))
        assertNecronWarriors(d, p, 1)
    }

    @Test
    fun eachArtifactSpellYouCastCostsOneLifeAndCreatesOneNecronWarrior() {
        val d = scenarioWithBiotransference("Sol Ring")
        val p = d.player1
        val firstRing = d.putCardInHand(p, "Sol Ring")
        val secondRing = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, firstRing).error)
        assertEquals(20, d.getLifeTotal(p))
        d.resolveAll()
        assertEquals(19, d.getLifeTotal(p))
        assertNecronWarriors(d, p, 1)

        assertNull(d.castSpell(p, secondRing).error)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(p))
        assertNecronWarriors(d, p, 2)
        assertTrue(d.permanentsNamed(p, "Sol Ring").containsAll(listOf(firstRing, secondRing)))
    }

    @Test
    fun castingANonartifactSpellDoesNotCostLifeOrCreateAWarrior() {
        val d = scenarioWithBiotransference("Shock")
        val p = d.player1
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)

        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(d.player2))
        assertNecronWarriors(d, p, 0)
    }

    @Test
    fun anArtifactSpellCastByAnOpponentDoesNotTriggerBiotransference() {
        val d = scenarioWithBiotransference("Sol Ring")
        val p = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val ring = d.putCardInHand(opponent, "Sol Ring")
        d.giveColorlessMana(opponent, 1)

        assertNull(d.castSpell(opponent, ring).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(opponent, "Sol Ring").contains(ring))
        assertEquals(20, d.getLifeTotal(p))
        assertNecronWarriors(d, p, 0)
    }
}

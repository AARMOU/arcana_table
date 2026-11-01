package com.arcanatable.customcards

// Trusted-main, real-Argentum acceptance scenarios for Dragon's Rage Channeler.
// Written from the printed Oracle, not the implementation:
// "Whenever you cast a noncreature spell, surveil 1.
//  Delirium — As long as there are four or more card types among cards in your graveyard,
//  this creature gets +2/+2, has flying, and attacks each combat if able."
// Cost {R}; 1/1 Human Shaman. Surveil is optional per card; only the controller's own
// noncreature spells trigger it; only the controller's own graveyard counts for delirium.
import com.arcanatable.customcards.cards.DragonsRageChanneler
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DragonsRageChannelerIndependentCertificationTest {
    private val pool = arrayOf("Sol Ring", "Grizzly Bears", "Llanowar Elves", "Forest", "Plains", "Shock", "Divination")

    @Test
    fun castingForOneRedEntersAsAOneOneHumanShamanWithoutDeliriumBonuses() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCardInHand(p, "Dragon's Rage Channeler")
        d.giveMana(p, Color.RED, 1)
        assertNull(d.castSpell(p, dragon).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(dragon))
        assertEquals(1, d.projected.getPower(dragon))
        assertEquals(1, d.projected.getToughness(dragon))
        assertTrue(d.projected.hasColor(dragon, Color.RED))
        assertTrue(d.projected.hasSubtype(dragon, "HUMAN"))
        assertTrue(d.projected.hasSubtype(dragon, "SHAMAN"))
        assertFalse(d.projected.hasKeyword(dragon, Keyword.FLYING))
    }

    @Test
    fun genericManaCannotPayTheRedCost() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCardInHand(p, "Dragon's Rage Channeler")
        d.giveColorlessMana(p, 1)
        assertNotNull(d.castSpell(p, dragon).error)

        assertTrue(d.getHand(p).contains(dragon))
        assertFalse(d.getPermanents(p).contains(dragon))
    }

    @Test
    fun castingANoncreatureSpellSurveilsOneAndTheCardCanGoToTheGraveyard() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardOnTopOfLibrary(p, "Forest")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll(Choices(pick = listOf("Forest")))

        assertTrue(d.getGraveyardCardNames(p).contains("Forest"))
        assertTrue("the noncreature spell itself still resolved", d.permanentsNamed(p, "Sol Ring").isNotEmpty())
    }

    @Test
    fun surveilMayLeaveTheCardOnTopOfTheLibrary() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardOnTopOfLibrary(p, "Forest")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll(Choices(pick = emptyList()))

        assertFalse(d.getGraveyardCardNames(p).contains("Forest"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
    }

    @Test
    fun surveilTriggersOnceForEachNoncreatureSpellCast() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardOnTopOfLibrary(p, "Plains")
        d.putCardOnTopOfLibrary(p, "Forest")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll(Choices(pick = listOf("Forest")))

        assertTrue(d.getGraveyardCardNames(p).contains("Forest"))
        assertFalse("surveil 1 looks at only one card", d.getGraveyardCardNames(p).contains("Plains"))
    }

    @Test
    fun castingACreatureSpellDoesNotSurveil() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardOnTopOfLibrary(p, "Forest")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN, 1)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll(Choices(pick = listOf("Forest")))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
        assertFalse(d.getGraveyardCardNames(p).contains("Forest"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Forest"))
    }

    @Test
    fun anOpponentsNoncreatureSpellDoesNotSurveil() {
        val d = customScenario(DragonsRageChanneler, *pool)
        d.putCreatureOnBattlefield(d.player1, "Dragon's Rage Channeler")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.putCardOnTopOfLibrary(d.player1, "Forest")
        d.putCardOnTopOfLibrary(d.player2, "Plains")
        val ring = d.putCardInHand(d.player2, "Sol Ring")
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.castSpell(d.player2, ring).error)
        d.resolveAll(Choices(pick = listOf("Forest", "Plains")))

        assertFalse(d.getGraveyardCardNames(d.player1).contains("Forest"))
        assertFalse(d.getGraveyardCardNames(d.player2).contains("Plains"))
    }

    @Test
    fun withoutDeliriumItStaysAOneOneWithoutFlyingAndMayStayHome() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Sol Ring")
        d.removeSummoningSickness(dragon)

        assertEquals(1, d.projected.getPower(dragon))
        assertEquals(1, d.projected.getToughness(dragon))
        assertFalse(d.projected.hasKeyword(dragon, Keyword.FLYING))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, emptyList(), d.player2).error)
    }

    @Test
    fun repeatedCardTypesCountOnlyOnce() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Llanowar Elves")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Forest")
        d.putCardInGraveyard(p, "Sol Ring")

        assertEquals(1, d.projected.getPower(dragon))
        assertEquals(1, d.projected.getToughness(dragon))
        assertFalse(d.projected.hasKeyword(dragon, Keyword.FLYING))
    }

    @Test
    fun fourCardTypesInTheGraveyardGiveTwoTwoAndFlying() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Sol Ring")
        d.putCardInGraveyard(p, "Shock")

        assertEquals(3, d.projected.getPower(dragon))
        assertEquals(3, d.projected.getToughness(dragon))
        assertTrue(d.projected.hasKeyword(dragon, Keyword.FLYING))
    }

    @Test
    fun fiveCardTypesStillGiveExactlyOneBonus() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Sol Ring")
        d.putCardInGraveyard(p, "Shock")
        d.putCardInGraveyard(p, "Divination")

        assertEquals(3, d.projected.getPower(dragon))
        assertEquals(3, d.projected.getToughness(dragon))
        assertTrue(d.projected.hasKeyword(dragon, Keyword.FLYING))
    }

    @Test
    fun anOpponentsGraveyardDoesNotCountTowardDelirium() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val dragon = d.putCreatureOnBattlefield(d.player1, "Dragon's Rage Channeler")
        d.putCardInGraveyard(d.player2, "Grizzly Bears")
        d.putCardInGraveyard(d.player2, "Plains")
        d.putCardInGraveyard(d.player2, "Sol Ring")
        d.putCardInGraveyard(d.player2, "Shock")

        assertEquals(1, d.projected.getPower(dragon))
        assertEquals(1, d.projected.getToughness(dragon))
        assertFalse(d.projected.hasKeyword(dragon, Keyword.FLYING))
    }

    @Test
    fun resolvingASorceryCanCompleteDeliriumAsAFourthType() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Sol Ring")
        assertEquals(1, d.projected.getPower(dragon))
        val divination = d.putCardInHand(p, "Divination")
        d.giveMana(p, Color.BLUE, 1)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, divination).error)
        d.resolveAll(Choices(pick = emptyList()))

        assertTrue(d.getGraveyardCardNames(p).contains("Divination"))
        assertEquals(3, d.projected.getPower(dragon))
        assertTrue(d.projected.hasKeyword(dragon, Keyword.FLYING))
    }

    @Test
    fun withDeliriumItMustAttackAndCannotStayHome() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Sol Ring")
        d.putCardInGraveyard(p, "Shock")
        d.removeSummoningSickness(dragon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNotNull(d.declareAttackers(p, emptyList(), d.player2).error)
    }

    @Test
    fun withDeliriumItAttacksAsAThreeThreeFlyerAndDealsThree() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Sol Ring")
        d.putCardInGraveyard(p, "Shock")
        d.removeSummoningSickness(dragon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(dragon), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(17, d.getLifeTotal(d.player2))
    }

    @Test
    fun withDeliriumAGroundCreatureCannotBlockIt() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Plains")
        d.putCardInGraveyard(p, "Sol Ring")
        d.putCardInGraveyard(p, "Shock")
        d.removeSummoningSickness(dragon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(dragon), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(dragon))).error)
    }

    @Test
    fun withoutDeliriumAGroundCreatureCanBlockIt() {
        val d = customScenario(DragonsRageChanneler, *pool)
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(dragon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(dragon), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(dragon))).error)
    }
}

package com.arcanatable.customcards

// Independent real-engine scenarios written only from Marneus Calgar's Oracle record:
// Double strike. Master Tactician — Whenever one or more tokens you control enter, draw a card.
// Chapter Master — {6}: Create two 2/2 white Astartes Warrior creature tokens with vigilance.
import com.arcanatable.customcards.cards.MarneusCalgar
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class MarneusCalgarIndependentCertificationTest {
    @Test
    fun castingRequiresTwoGenericWhiteBlueAndBlackAndEntersAsAThreeFiveDoubleStriker() {
        val d = customScenario(MarneusCalgar)
        val player = d.player1
        val calgar = d.putCardInHand(player, "Marneus Calgar")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.WHITE)
        d.giveMana(player, Color.BLUE)

        assertNotNull("The {B} in {2}{W}{U}{B} cannot be paid with generic mana",
            d.castSpell(player, calgar).error)
        d.giveMana(player, Color.BLACK)
        assertNull(d.castSpell(player, calgar).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(calgar))
        assertEquals(3, d.projected.getPower(calgar))
        assertEquals(5, d.projected.getToughness(calgar))
        assertTrue(d.projected.hasKeyword(calgar, Keyword.DOUBLE_STRIKE))
        assertTrue(d.projected.hasSubtype(calgar, "ASTARTES"))
        assertTrue(d.projected.hasSubtype(calgar, "WARRIOR"))
    }

    @Test
    fun doubleStrikeDealsCombatDamageInBothDamageSteps() {
        val d = customScenario(MarneusCalgar)
        val calgar = d.putCreatureOnBattlefield(d.player1, "Marneus Calgar")
        d.removeSummoningSickness(calgar)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(calgar), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(14, d.getLifeTotal(d.player2))
        assertTrue(d.getPermanents(d.player1).contains(calgar))
    }

    @Test
    fun chapterMasterCanBeActivatedAtOpponentPriorityAndTwoTokensDrawOnlyOneCard() {
        val d = customScenario(MarneusCalgar)
        val player = d.player1
        val calgar = d.putCreatureOnBattlefield(player, "Marneus Calgar")
        val handBefore = d.getHandSize(player)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        d.giveColorlessMana(player, 6)

        assertNull(d.activate(player, calgar).error)
        d.resolveAll()

        val tokens = d.permanentsNamed(player, "Astartes Warrior")
        assertEquals(2, tokens.size)
        tokens.forEach { token ->
            assertEquals(2, d.projected.getPower(token))
            assertEquals(2, d.projected.getToughness(token))
            assertEquals(setOf(Color.WHITE), d.projected.getColors(token))
            assertTrue(d.projected.hasSubtype(token, "ASTARTES"))
            assertTrue(d.projected.hasSubtype(token, "WARRIOR"))
            assertTrue(d.projected.hasKeyword(token, Keyword.VIGILANCE))
        }
        assertEquals("The two tokens entering together trigger one draw", handBefore + 1,
            d.getHandSize(player))
        assertFalse("Chapter Master has no tap cost", d.isTapped(calgar))
    }

    @Test
    fun chapterMasterCannotBeActivatedWithOnlyFiveMana() {
        val d = customScenario(MarneusCalgar)
        val player = d.player1
        val calgar = d.putCreatureOnBattlefield(player, "Marneus Calgar")
        d.giveColorlessMana(player, 5)

        assertNotNull(d.activate(player, calgar).error)

        assertTrue(d.permanentsNamed(player, "Astartes Warrior").isEmpty())
        assertFalse(d.isTapped(calgar))
    }

    @Test
    fun onlyMarneusControllersCanActivateChapterMaster() {
        val d = customScenario(MarneusCalgar)
        val calgar = d.putCreatureOnBattlefield(d.player1, "Marneus Calgar")
        d.giveColorlessMana(d.player2, 6)

        assertNotNull(d.activate(d.player2, calgar).error)

        assertTrue(d.permanentsNamed(d.player1, "Astartes Warrior").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Astartes Warrior").isEmpty())
    }

    @Test
    fun tokensEnteringUnderAnOpponentsControlDoNotTriggerMasterTactician() {
        val d = customScenario(MarneusCalgar, "Raise the Alarm")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Marneus Calgar")
        val handBefore = d.getHandSize(player)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val raiseTheAlarm = d.putCardInHand(opponent, "Raise the Alarm")
        d.giveMana(opponent, Color.WHITE)
        d.giveColorlessMana(opponent, 1)

        assertNull(d.castSpell(opponent, raiseTheAlarm).error)
        d.resolveAll()

        assertEquals(2, d.permanentsNamed(opponent, "Soldier").size)
        assertEquals("Only tokens controlled by Marneus's controller trigger the ability",
            handBefore, d.getHandSize(player))
    }

    @Test
    fun aNontokenCreatureEnteringUnderYourControlDoesNotTriggerMasterTactician() {
        val d = customScenario(MarneusCalgar, "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Marneus Calgar")
        val handBefore = d.getHandSize(player)
        val bear = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, bear).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
        assertEquals("A nontoken creature entering is not a token event",
            handBefore - 1, d.getHandSize(player))
    }
}

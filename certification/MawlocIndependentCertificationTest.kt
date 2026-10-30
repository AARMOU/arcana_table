package com.arcanatable.customcards

// Independent real-Argentum checks for Mawloc (queue #397, Copilot PR #405).
// Authored from Wizards' 40K Commander release notes and their Mawloc ruling:
// https://magic.wizards.com/en/news/feature/warhammer-40000-commander-release-notes-2022-09-19
// The fight target must be exiled if it dies later that turn, even after Mawloc leaves.
// No Copilot implementation or Copilot-authored tests were consulted when writing these cases.
import com.arcanatable.customcards.cards.Mawloc
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MawlocIndependentCertificationTest {
    private fun GameTestDriver.castMawlocForX(x: Int, target: EntityId? = null): EntityId {
        val p = player1
        val mawloc = putCardInHand(p, "Mawloc")
        giveMana(p, Color.RED)
        giveMana(p, Color.GREEN)
        if (x > 0) giveColorlessMana(p, x)
        assertNull(castXSpell(p, mawloc, x).error)
        resolveAll(Choices(targets = target?.let { listOf(it) } ?: emptyList()))
        return mawloc
    }

    @Test
    fun xMustBePaidAndEntersWithExactlyXPlusOneCounters() {
        val d = customScenario(Mawloc)
        val p = d.player1
        val card = d.putCardInHand(p, "Mawloc")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)
        assertNotNull("X=3 needs three generic mana in addition to R and G",
            d.castXSpell(p, card, 3).error)
        assertFalse(d.getPermanents(p).contains(card))
        d.giveColorlessMana(p, 1)
        assertNull(d.castXSpell(p, card, 3).error)
        d.resolveAll()
        assertEquals(5, d.projected.getPower(card))
        assertEquals(5, d.projected.getToughness(card))
    }

    @Test
    fun ravenousDrawsExactlyOneCardAtXFive() {
        val d = customScenario(Mawloc)
        val p = d.player1
        val card = d.putCardInHand(p, "Mawloc")
        val before = d.getHandSize(p)
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)
        assertNull(d.castXSpell(p, card, 5).error)
        d.resolveAll()
        assertEquals(7, d.projected.getPower(card))
        assertEquals(7, d.projected.getToughness(card))
        assertEquals("Casting costs one card; Ravenous draws exactly one", before, d.getHandSize(p))
    }

    @Test
    fun optionalFightDoesNotHarmOwnCreatureWhenNoOpponentCreatureExists() {
        val d = customScenario(Mawloc, "Grizzly Bears")
        val ownBear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val mawloc = d.castMawlocForX(0)
        assertTrue(d.getPermanents(d.player1).contains(ownBear))
        assertTrue(d.getPermanents(d.player1).contains(mawloc))
        assertEquals(2, d.projected.getPower(mawloc))
        assertEquals(0, d.getExile(d.player2).size)
    }

    @Test
    fun fightDeathExilesOpponentCreatureInsteadOfSendingItToGraveyard() {
        val d = customScenario(Mawloc, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val mawloc = d.castMawlocForX(1, bear) // 3/3 fights 2/2 and survives.
        assertTrue(d.getPermanents(d.player1).contains(mawloc))
        assertEquals(3, d.projected.getPower(mawloc))
        assertTrue("The creature killed in Mawloc's fight must be exiled", d.getExile(d.player2).contains(bear))
        assertFalse(d.getGraveyard(d.player2).contains(bear))
    }

    @Test
    fun simultaneousFightDeathExilesOnlyOpponentCreature() {
        val d = customScenario(Mawloc, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val mawloc = d.castMawlocForX(0, bear) // Two 2/2s fight; both take lethal damage.
        assertTrue("Mawloc itself dies normally", d.getGraveyard(d.player1).contains(mawloc))
        assertFalse(d.getExile(d.player1).contains(mawloc))
        assertTrue("Only the target receives exile-on-death", d.getExile(d.player2).contains(bear))
        assertFalse(d.getGraveyard(d.player2).contains(bear))
    }

    @Test
    fun laterDeathSameTurnIsStillExiledEvenAfterMawlocHasDied() {
        val d = customScenario(Mawloc, "Giant Spider", "Shock")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider") // 2/4.
        val mawloc = d.castMawlocForX(0, spider) // Mawloc dies; Spider takes two damage.
        assertTrue(d.getGraveyard(d.player1).contains(mawloc))
        assertTrue(d.getPermanents(d.player2).contains(spider))
        val shock = d.putCardInHand(d.player1, "Shock")
        d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpell(d.player1, shock, listOf(spider)).error)
        d.resolveAll()
        assertTrue("WotC ruling: replacement persists if Mawloc has left play",
            d.getExile(d.player2).contains(spider))
        assertFalse(d.getGraveyard(d.player2).contains(spider))
    }

    @Test
    fun exileReplacementExpiresAfterThatTurn() {
        val d = customScenario(Mawloc, "Giant Spider", "Murder")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.castMawlocForX(0, spider)
        assertTrue(d.getPermanents(d.player2).contains(spider))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)
        assertNull(d.castSpell(d.player2, murder, listOf(spider)).error)
        d.resolveAll()
        assertTrue("A later-turn death must go to graveyard", d.getGraveyard(d.player2).contains(spider))
        assertFalse(d.getExile(d.player2).contains(spider))
    }
}

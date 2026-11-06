package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Furycalm Snarl — the reveal-land "as this enters" replacement on the shipped engine (Argentum's library definition, exactly as the app plays it).
 * Shared checks: [RevealLandChecks]; the same six cases run for every land of the cycle.
 */
class FurycalmSnarlScenarioTest {

    private fun scenario() = scenario("Furycalm Snarl", "Mountain", "Plains", "Island").apply { registerCard(RevealLandChecks.ReturnLandTappedTest) }

    @Test fun revealingAMountainOrAPlainsEntersUntappedAndShowsEveryPlayer() =
        RevealLandChecks.revealingAMatchingCardEntersUntapped(scenario(), "Furycalm Snarl", "Mountain", "Plains")

    @Test fun decliningToRevealEntersTapped() =
        RevealLandChecks.decliningEntersTapped(scenario(), "Furycalm Snarl", "Mountain")

    @Test fun aIslandAndAnotherCopyAreNotOffered() =
        RevealLandChecks.nonMatchingCardsAreNotOffered(scenario(), "Furycalm Snarl", "Island")

    @Test fun emptyHandEntersTappedWithoutAQuestion() =
        RevealLandChecks.emptyHandEntersTappedWithoutAQuestion(scenario(), "Furycalm Snarl")

    @Test fun anEffectThatPutsItOntoTheBattlefieldTappedWins() =
        RevealLandChecks.putOntoTheBattlefieldTappedStaysTapped(scenario(), "Furycalm Snarl", "Mountain")

    @Test fun hasOneManaAbilityPerColor() {
        val d = scenario()
        val land = d.putPermanentOnBattlefield(d.player1, "Furycalm Snarl")
        assertEquals(2, d.abilityActions(d.player1, land).size)
    }

    @Test fun fourPlayerRevealIsShownToEverySeat() =
        RevealLandChecks.fourPlayerRevealIsPublic("Furycalm Snarl", "Mountain")
}

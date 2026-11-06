package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Port Town — the reveal-land "as this enters" replacement on the shipped engine (Argentum's library definition, exactly as the app plays it).
 * Shared checks: [RevealLandChecks]; the same six cases run for every land of the cycle.
 */
class PortTownScenarioTest {

    private fun scenario() = scenario("Port Town", "Plains", "Island", "Forest").apply { registerCard(RevealLandChecks.ReturnLandTappedTest) }

    @Test fun revealingAPlainsOrAIslandEntersUntappedAndShowsEveryPlayer() =
        RevealLandChecks.revealingAMatchingCardEntersUntapped(scenario(), "Port Town", "Plains", "Island")

    @Test fun decliningToRevealEntersTapped() =
        RevealLandChecks.decliningEntersTapped(scenario(), "Port Town", "Plains")

    @Test fun aForestAndAnotherCopyAreNotOffered() =
        RevealLandChecks.nonMatchingCardsAreNotOffered(scenario(), "Port Town", "Forest")

    @Test fun emptyHandEntersTappedWithoutAQuestion() =
        RevealLandChecks.emptyHandEntersTappedWithoutAQuestion(scenario(), "Port Town")

    @Test fun anEffectThatPutsItOntoTheBattlefieldTappedWins() =
        RevealLandChecks.putOntoTheBattlefieldTappedStaysTapped(scenario(), "Port Town", "Plains")

    @Test fun hasOneManaAbilityPerColor() {
        val d = scenario()
        val land = d.putPermanentOnBattlefield(d.player1, "Port Town")
        assertEquals(2, d.abilityActions(d.player1, land).size)
    }

    @Test fun fourPlayerRevealIsShownToEverySeat() =
        RevealLandChecks.fourPlayerRevealIsPublic("Port Town", "Island")
}

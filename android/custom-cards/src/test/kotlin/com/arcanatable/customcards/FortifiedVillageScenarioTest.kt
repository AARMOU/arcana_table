package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FortifiedVillage
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Fortified Village — the reveal-land "as this enters" replacement on the shipped engine (Arcana's custom definition, round-tripped through card JSON like the app).
 * Shared checks: [RevealLandChecks]; the same six cases run for every land of the cycle.
 */
class FortifiedVillageScenarioTest {

    private fun scenario() = customScenario(FortifiedVillage, "Forest", "Plains", "Mountain").apply { registerCard(RevealLandChecks.ReturnLandTappedTest) }

    @Test fun revealingAForestOrAPlainsEntersUntappedAndShowsEveryPlayer() =
        RevealLandChecks.revealingAMatchingCardEntersUntapped(scenario(), "Fortified Village", "Forest", "Plains")

    @Test fun decliningToRevealEntersTapped() =
        RevealLandChecks.decliningEntersTapped(scenario(), "Fortified Village", "Forest")

    @Test fun aMountainAndAnotherCopyAreNotOffered() =
        RevealLandChecks.nonMatchingCardsAreNotOffered(scenario(), "Fortified Village", "Mountain")

    @Test fun emptyHandEntersTappedWithoutAQuestion() =
        RevealLandChecks.emptyHandEntersTappedWithoutAQuestion(scenario(), "Fortified Village")

    @Test fun anEffectThatPutsItOntoTheBattlefieldTappedWins() =
        RevealLandChecks.putOntoTheBattlefieldTappedStaysTapped(scenario(), "Fortified Village", "Forest")

    @Test fun hasOneManaAbilityPerColor() {
        val d = scenario()
        val land = d.putPermanentOnBattlefield(d.player1, "Fortified Village")
        assertEquals(2, d.abilityActions(d.player1, land).size)
    }

    @Test fun fourPlayerRevealIsShownToEverySeat() =
        RevealLandChecks.fourPlayerRevealIsPublic("Fortified Village", "Forest", com.wingedsheep.sdk.tooling.CardLoader.fromJsonPreservingIds(exportLine(FortifiedVillage)))
}

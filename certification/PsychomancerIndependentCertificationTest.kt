package com.arcanatable.customcards

// Trusted-main, real-Argentum acceptance scenarios for Psychomancer.
// Queue #148; Copilot candidate #224. Assertions follow the printed Oracle:
// a controlled nontoken artifact moving from battlefield to graveyard or exile
// triggers one targeted drain, including Psychomancer itself. Other zones,
// nonartifact creatures, and artifacts controlled by opponents do not qualify.
// No card implementation, runtime engine, or production behavior is modified.
import com.arcanatable.customcards.cards.Psychomancer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class PsychomancerIndependentCertificationTest {
    private fun GameTestDriver.destroyCreature(victim: com.wingedsheep.sdk.model.EntityId) {
        val murder = putCardInHand(player1, "Murder")
        giveMana(player1, Color.BLACK, 2)
        giveColorlessMana(player1, 1)
        assertNull(castSpell(player1, murder, listOf(victim)).error)
    }

    @Test fun dyingPsychomancerDrainsOpponentOnce() {
        val d = customScenario(Psychomancer, "Murder")
        val self = d.putCreatureOnBattlefield(d.player1, "Psychomancer")
        d.destroyCreature(self)
        d.resolveAll(Choices(targets = listOf(d.player2)))

        assertTrue(d.getGraveyard(d.player1).contains(self))
        d.assertLifeTotal(d.player1, 21)
        d.assertLifeTotal(d.player2, 19)
    }

    @Test fun anotherControlledNontokenArtifactAddsItsOwnTrigger() {
        val d = customScenario(Psychomancer, "Murder")
        d.putCreatureOnBattlefield(d.player1, "Psychomancer")
        val dying = d.putCreatureOnBattlefield(d.player1, "Psychomancer")
        d.destroyCreature(dying)
        d.resolveAll(Choices(targets = listOf(d.player2)))

        // One trigger from the dying source and one from the surviving source.
        d.assertLifeTotal(d.player1, 22)
        d.assertLifeTotal(d.player2, 18)
    }

    @Test fun opponentsArtifactDeathDoesNotTriggerOurPsychomancer() {
        val d = customScenario(Psychomancer, "Murder")
        d.putCreatureOnBattlefield(d.player1, "Psychomancer")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Psychomancer")
        d.destroyCreature(enemy)
        d.resolveAll(Choices(targets = listOf(d.player1)))

        // Opponent's dying Psychomancer triggers for its controller, not ours.
        // A phantom trigger for our Psychomancer would incorrectly offset this.
        d.assertLifeTotal(d.player1, 19)
        d.assertLifeTotal(d.player2, 21)
    }

    @Test fun exilingPsychomancerAlsoDrainsOnce() {
        val d = customScenario(Psychomancer, "Path to Exile")
        val self = d.putCreatureOnBattlefield(d.player1, "Psychomancer")
        val path = d.putCardInHand(d.player1, "Path to Exile")
        d.giveMana(d.player1, Color.WHITE)
        assertNull(d.castSpell(d.player1, path, listOf(self)).error)
        d.resolveAll(Choices(targets = listOf(d.player2), yes = false))

        assertTrue(d.getExile(d.player1).contains(self))
        d.assertLifeTotal(d.player1, 21)
        d.assertLifeTotal(d.player2, 19)
    }

    @Test fun ordinaryNonartifactCreatureDeathDoesNotDrain() {
        val d = customScenario(Psychomancer, "Murder", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Psychomancer")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.destroyCreature(bear)
        d.resolveAll()

        assertTrue(d.getGraveyard(d.player1).contains(bear))
        d.assertLifeTotal(d.player1, 20)
        d.assertLifeTotal(d.player2, 20)
    }
}

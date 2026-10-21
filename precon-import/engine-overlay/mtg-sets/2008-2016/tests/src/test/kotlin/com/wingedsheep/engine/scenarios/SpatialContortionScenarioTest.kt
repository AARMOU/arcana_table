package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Spatial Contortion: positive power and negative toughness in one targeted EOT effect. */
class SpatialContortionScenarioTest : ScenarioTestBase() {
    init {
        test("applies +3/-3 to exactly the targeted creature") {
            val g = scenario()
                .withPlayers()
                .withCardInHand(1, "Spatial Contortion")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Wastes", 2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withActivePlayer(1)
                .withPriorityPlayer(1)
                .build()

            val bears = g.findPermanent("Grizzly Bears")!!
            g.castSpell(1, "Spatial Contortion", bears).error shouldBe null
            g.resolveStack()

            // 2/2 targeted Bear goes to graveyard for toughness <= 0.
            g.findPermanent("Grizzly Bears", 1) shouldBe null
            // Opponent's unrelated Bear is unaffected.
            val other = g.findPermanent("Grizzly Bears", 2)!!
            g.state.projectedState.getPower(other) shouldBe 2
            g.state.projectedState.getToughness(other) shouldBe 2
        }
    }
}

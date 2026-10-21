package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Spatial Contortion: its +3 power and -3 toughness must resolve on the target, not approximately. */
class SpatialContortionScenarioTest : ScenarioTestBase() {
    init {
        test("target Giant Spider becomes 5/1 for this turn") {
            val g = scenario()
                .withPlayers()
                .withCardInHand(1, "Spatial Contortion")
                .withCardOnBattlefield(1, "Giant Spider")
                .withLandsOnBattlefield(1, "Wastes", 2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withActivePlayer(1)
                .withPriorityPlayer(1)
                .build()

            val spider = g.findPermanent("Giant Spider")!!
            g.state.projectedState.getPower(spider) shouldBe 2
            g.state.projectedState.getToughness(spider) shouldBe 4

            g.castSpell(1, "Spatial Contortion", spider).error shouldBe null
            g.resolveStack()

            g.state.projectedState.getPower(spider) shouldBe 5
            g.state.projectedState.getToughness(spider) shouldBe 1
        }

        test("negative toughness can kill the target through state-based actions") {
            val g = scenario()
                .withPlayers()
                .withCardInHand(1, "Spatial Contortion")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Wastes", 2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withActivePlayer(1)
                .withPriorityPlayer(1)
                .build()

            val bear = g.findPermanent("Grizzly Bears")!!
            g.castSpell(1, "Spatial Contortion", bear).error shouldBe null
            g.resolveStack()

            g.findPermanent("Grizzly Bears") shouldBe null
        }
    }
}

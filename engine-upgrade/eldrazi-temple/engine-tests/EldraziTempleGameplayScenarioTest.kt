package com.wingedsheep.engine.scenarios

import com.arcanatable.customcards.cards.EldraziTemple
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * #152: actual land activation -> restricted pool -> engine spell/ability payment.
 * Separate from the pure ManaRestriction predicate tests. The source-only candidate
 * is intentionally not registered in Arcana's shipped index or compiled against old jars.
 */
class EldraziTempleGameplayScenarioTest : ScenarioTestBase() {
    init {
        val colorlessEldrazi = card("Temple Test Colorless Eldrazi") {
            manaCost = "{C}{C}"
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
        }
        val coloredEldrazi = card("Temple Test Colored Eldrazi") {
            manaCost = "{1}{U}"
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
        }
        val otherColorless = card("Temple Test Colorless Human") {
            manaCost = "{C}{C}"
            typeLine = "Creature — Human"
            power = 2
            toughness = 2
        }
        val colorlessAbility = card("Temple Test Eldrazi Ability") {
            manaCost = "{2}"
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            activatedAbility {
                cost = Costs.Mana("{C}{C}")
                effect = Effects.GainLife(1)
            }
        }
        val coloredAbility = card("Temple Test Colored Ability") {
            manaCost = "{U}"
            typeLine = "Creature — Eldrazi"
            power = 1
            toughness = 1
            activatedAbility {
                cost = Costs.Mana("{C}{C}")
                effect = Effects.GainLife(1)
            }
        }
        listOf(EldraziTemple, colorlessEldrazi, coloredEldrazi, otherColorless,
            colorlessAbility, coloredAbility).forEach(cardRegistry::register)

        val unrestricted = EldraziTemple.activatedAbilities[0].id
        val restricted = EldraziTemple.activatedAbilities[1].id

        test("second tap ability produces exactly two restricted colorless mana usable for a colorless Eldrazi spell") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Eldrazi Temple")
                .withCardInHand(1, "Temple Test Colorless Eldrazi")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val land = game.findPermanent("Eldrazi Temple")!!
            game.execute(ActivateAbility(game.player1Id, land, restricted)).error shouldBe null
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
                .restrictedMana.size shouldBe 2
            game.castSpell(1, "Temple Test Colorless Eldrazi").error shouldBe null
            game.resolveStack()
            game.findPermanent("Temple Test Colorless Eldrazi") shouldNotBe null
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
                .restrictedMana.size shouldBe 0
        }

        test("restricted tap mana cannot cast a non-Eldrazi colorless spell") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Eldrazi Temple")
                .withCardInHand(1, "Temple Test Colorless Human")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val land = game.findPermanent("Eldrazi Temple")!!
            game.execute(ActivateAbility(game.player1Id, land, restricted)).error shouldBe null
            game.castSpell(1, "Temple Test Colorless Human").error shouldNotBe null
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
                .restrictedMana.size shouldBe 2
        }

        test("restricted tap mana cannot pay generic portion of a colored Eldrazi spell") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Eldrazi Temple")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(1, "Temple Test Colored Eldrazi")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val land = game.findPermanent("Eldrazi Temple")!!
            game.execute(ActivateAbility(game.player1Id, land, restricted)).error shouldBe null
            game.castSpell(1, "Temple Test Colored Eldrazi").error shouldNotBe null
        }

        test("colorless Eldrazi source ability can spend restricted mana but colored Eldrazi source cannot") {
            fun activate(sourceName: String): Pair<Boolean, Int> {
                val game = scenario().withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Eldrazi Temple")
                    .withCardOnBattlefield(1, sourceName)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val land = game.findPermanent("Eldrazi Temple")!!
                val creature = game.findPermanent(sourceName)!!
                val ability = (if (sourceName == "Temple Test Eldrazi Ability") colorlessAbility
                    else coloredAbility).activatedAbilities.single().id
                game.execute(ActivateAbility(game.player1Id, land, restricted)).error shouldBe null
                val before = game.getLifeTotal(1)
                val result = game.execute(ActivateAbility(game.player1Id, creature, ability))
                if (result.error == null) game.resolveStack()
                return Pair(result.error == null, game.getLifeTotal(1) - before)
            }
            activate("Temple Test Eldrazi Ability") shouldBe Pair(true, 1)
            activate("Temple Test Colored Ability") shouldBe Pair(false, 0)
        }

        test("first tap ability is unrestricted and one Temple cannot tap twice") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Eldrazi Temple")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val land = game.findPermanent("Eldrazi Temple")!!
            game.execute(ActivateAbility(game.player1Id, land, unrestricted)).error shouldBe null
            val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.colorless shouldBe 1
            pool.restrictedMana.size shouldBe 0
            game.execute(ActivateAbility(game.player1Id, land, restricted)).error shouldNotBe null
        }
    }
}

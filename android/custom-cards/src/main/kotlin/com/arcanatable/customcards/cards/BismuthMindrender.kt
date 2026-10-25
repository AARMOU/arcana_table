package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.model.Rarity

/**
 * Bismuth Mindrender — M3C #51.
 */
val BismuthMindrender = card("Bismuth Mindrender") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Eldrazi"
    power = 4
    toughness = 3
    oracleText = "Devoid (This card has no color.)\nMenace\nWhenever this creature deals combat damage to a player, that player exiles cards from the top of their library until they exile a nonland card. You may cast that card by paying life equal to the spell's mana value rather than paying its mana cost."

    keywords(Keyword.DEVOID, Keyword.MENACE)

    //   combines implemented pieces:
    //     ~1.00 "whenever ~ deals combat damage to a player," ← Aberrant [custom]: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Aberrant.kt (test: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/AberrantScenarioTest.kt)
    //     ~1.00 "that player exiles cards from the top of their library until they exile a nonland card." ← Black Widow, Super Spy [library]: https://github.com/wingedsheep/argentum-engine/blob/61e71e586b100faa661f86cac827ac3c3bbb5a78/mtg-sets/2026/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/msh/cards/BlackWidowSuperSpy.kt
    //     ~0.81 "you may cast that card by paying life equal to the spell's mana value rather than paying its mana cost." ← Amped Raptor [library]: https://github.com/wingedsheep/argentum-engine/blob/61e71e586b100faa661f86cac827ac3c3bbb5a78/mtg-sets/2024/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/mh3/cards/AmpedRaptor.kt

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.Pipeline {
            val walk = gatherUntilMatch(GameObjectFilter.Nonland, player = Player.TriggeringPlayer)
            exile(walk.revealed)
            run(Effects.May(Effects.CastFromCollectionByPaying(walk.match, Costs.additional.PayLifeEqualToManaValueOfSpell)))
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "51"
        artist = "Maxime Minard"
        imageUri = "https://cards.scryfall.io/normal/front/d/1/d1ee8ead-df6a-4c02-83f7-2902d97132b8.jpg?1783911423"
    }
}

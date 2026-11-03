package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.SelectionRestriction
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Technomancer — Warhammer 40,000 Commander #61 (Necron Dynasties). {5}{B}{B} Artifact Creature — Necron
 * Wizard, 5/1.
 *
 * When this creature enters, mill three cards, then return any number of artifact creature cards with
 * total mana value 6 or less from your graveyard to the battlefield.
 *
 * One untargeted pipeline: `mill(3)`, then gather every artifact creature card in your graveyard (read
 * after the mill, so the cards just milled and the ones already there are both eligible), then
 * `chooseAnyNumber` with `SelectionRestriction.TotalManaValueAtMost(6)` (Ao, the Dawn Sky / Lively Dirge;
 * the decision carries the cap and the engine refuses a selection over it; zero cards is a legal choice),
 * then one simultaneous move to the battlefield.
 *
 * Official rulings (2022-10-07, Scryfall):
 *  - "The artifact creature cards you choose don't need to be cards that you milled. You may choose cards
 *    that were already in your graveyard before you milled."
 *  - "If a card in your graveyard has an {X} in its mana cost, X is zero."
 */
val Technomancer = card("Technomancer") {
    manaCost = "{5}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron Wizard"
    power = 5
    toughness = 1
    oracleText = "When this creature enters, mill three cards, then return any number of artifact creature " +
        "cards with total mana value 6 or less from your graveyard to the battlefield."

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.Pipeline {
            mill(3)
            val inGraveyard = gather(
                CardSource.FromZone(zone = Zone.GRAVEYARD, player = Player.You, filter = GameObjectFilter.ArtifactCreature)
            )
            val returned = chooseAnyNumber(
                from = inGraveyard,
                restrictions = listOf(SelectionRestriction.TotalManaValueAtMost(6)),
                prompt = "Return any number of artifact creature cards with total mana value 6 or less",
                selectedLabel = "Return to the battlefield",
            )
            move(returned, CardDestination.ToZone(Zone.BATTLEFIELD, Player.You))
        }
        description = "When this creature enters, mill three cards, then return any number of artifact " +
            "creature cards with total mana value 6 or less from your graveyard to the battlefield."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "61"
        artist = "David Álvarez"
        imageUri = "https://cards.scryfall.io/normal/front/c/1/c160c1f7-714e-444a-ab74-c64c3a099a48.jpg?1783920903"
    }
}

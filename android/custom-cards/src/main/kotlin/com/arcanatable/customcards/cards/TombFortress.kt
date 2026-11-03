package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Tomb Fortress — Warhammer 40,000 Commander #168 (Necron Dynasties). Land.
 *
 * This land enters tapped.
 * {T}: Add {B}.
 * {2}{B}{B}{B}, {T}, Exile this land: Mill four cards, then return a creature card from your graveyard
 * to the battlefield. Activate only as a sorcery.
 *
 * Port of Karfell's last ability with `Costs.ExileSelf` in place of the sacrifice, sorcery timing, and
 * the default (untapped) battlefield placement. The returned creature isn't targeted: after the mill it
 * is chosen from every creature card in your graveyard. Official ruling (2022-10-07): "The creature
 * card that you return to the battlefield doesn't have to be one of the cards you milled. It can be a
 * card that was already in your graveyard before you milled." With no creature card there, the mill
 * still happens and nothing returns.
 */
val TombFortress = card("Tomb Fortress") {
    manaCost = ""
    colorIdentity = "B"
    typeLine = "Land"
    oracleText = "This land enters tapped.\n{T}: Add {B}.\n{2}{B}{B}{B}, {T}, Exile this land: Mill four cards, then return a creature card from your graveyard to the battlefield. Activate only as a sorcery."

    replacementEffect(EntersTapped())

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLACK, 1)
        manaAbility = true
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}{B}{B}{B}"), Costs.Tap, Costs.ExileSelf)
        timing = TimingRule.SorcerySpeed
        effect = Effects.Pipeline {
            mill(4)
            val creatures = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.Creature))
            val chosen = chooseExactly(1, from = creatures, prompt = "Choose a creature card to return to the battlefield")
            move(chosen, CardDestination.ToZone(Zone.BATTLEFIELD))
        }
        description = "Mill four cards, then return a creature card from your graveyard to the battlefield."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "168"
        artist = "Josu Solano"
        imageUri = "https://cards.scryfall.io/normal/front/5/f/5ffd1a43-5613-4058-9e51-fd3cc1585651.jpg?1783920809"
        ruling(
            "2022-10-07",
            "The creature card that you return to the battlefield doesn't have to be one of the cards you milled. " +
                "It can be a card that was already in your graveyard before you milled."
        )
    }
}

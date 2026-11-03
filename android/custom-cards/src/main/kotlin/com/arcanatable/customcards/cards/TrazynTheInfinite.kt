package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.DonorCards
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.HasAllActivatedAbilitiesOfCards

/**
 * Trazyn the Infinite — Warhammer 40,000 Commander #65 (Necron Dynasties). {4}{B}{B} Legendary Artifact
 * Creature — Necron, 4/6.
 *
 * Deathtouch. Prismatic Gallery — As long as Trazyn is on the battlefield, it has all activated abilities
 * of all artifact cards in your graveyard.
 *
 * Prismatic Gallery is Necrotic Ooze's shape with Thranduil's pool: `HasAllActivatedAbilitiesOfCards` over
 * `DonorCards.YOUR_GRAVEYARD` (the graveyard of Trazyn's current controller, re-read live) narrowed to
 * `GameObjectFilter.Artifact`, granted to Trazyn itself. The engine grants each ability with Trazyn as its
 * source (CR 113.7), so a `{T}` cost taps Trazyn and "Sacrifice this" sacrifices Trazyn. Mana abilities are
 * included (the mana-ability enumerator and the auto-pay solver both read the same grant). The artifact
 * test reads the card's types off the battlefield, which include cross-zone `GrantCardType` grants, so with
 * Biotransference a creature card in your graveyard is an artifact card and lends its abilities.
 *
 * Official rulings (2022-10-07, Scryfall):
 *  - "Trazyn the Infinite gains only activated abilities. It doesn't gain keyword abilities (unless those
 *    keyword abilities are activated), triggered abilities, or static abilities."
 *  - Activated abilities contain a colon; some keywords are activated abilities and have colons in their
 *    reminder text.
 *  - If an activated ability of a card in a graveyard references the card it's printed on by name, treat
 *    Trazyn's version of that ability as though it referenced Trazyn by name instead.
 */
val TrazynTheInfinite = card("Trazyn the Infinite") {
    manaCost = "{4}{B}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Artifact Creature — Necron"
    power = 4
    toughness = 6
    oracleText = "Deathtouch\nPrismatic Gallery — As long as Trazyn is on the battlefield, it has all " +
        "activated abilities of all artifact cards in your graveyard."

    keywords(Keyword.DEATHTOUCH)

    staticAbility {
        ability = HasAllActivatedAbilitiesOfCards(
            donors = DonorCards.YOUR_GRAVEYARD,
            cardFilter = GameObjectFilter.Artifact,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "65"
        artist = "Michał Miłkowski"
        imageUri = "https://cards.scryfall.io/normal/front/1/a/1ae95cfb-1c3e-43cc-acfa-68f25b0f6e52.jpg?1783920903"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Ghost Ark — Warhammer 40,000 Commander #156 (Necron Dynasties). {4} Artifact — Vehicle, 3/3.
 *
 * Flying
 * Repair Barge — Whenever this Vehicle becomes crewed, each artifact creature card in your graveyard
 * gains unearth {3} until end of turn.
 * Crew 2
 *
 * Repair Barge is `Triggers.self.becomesCrewed()` (every time, not only the first each turn). On
 * resolution it gathers the artifact creature cards in your graveyard **then** (official ruling
 * 2022-10-07: cards that reach the graveyard later don't gain unearth; CR 611.2c) and grants each the
 * exact ability a printed `unearth("{3}")` has, through `GrantActivatedAbility` onto graveyard cards
 * (engine patch 0036). It ends at cleanup, and a card that leaves the graveyard and comes back is a
 * new object without it. Unearth itself is the shared `unearthAbility`: sorcery speed, returns with
 * haste, exiled at the next end step or if it would leave the battlefield. Being crewed twice grants
 * the same ability again, which changes nothing.
 */
val GhostArk = card("Ghost Ark") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact — Vehicle"
    power = 3
    toughness = 3
    oracleText = "Flying\nRepair Barge — Whenever this Vehicle becomes crewed, each artifact creature card in your graveyard gains unearth {3} until end of turn.\nCrew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)"

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.becomesCrewed()
        effect = Effects.Pipeline {
            val cards = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.ArtifactCreature))
            run(
                Effects.ForEachInCollection(
                    cards,
                    Effects.GrantActivatedAbility(unearthAbility(ManaCost.parse("{3}")), EffectTarget.IterationEntity),
                ),
            )
        }
        description = "Repair Barge — Whenever this Vehicle becomes crewed, each artifact creature card in your graveyard gains unearth {3} until end of turn."
    }

    keywordAbility(KeywordAbility.crew(2))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "156"
        artist = "Alexey Kruglov"
        imageUri = "https://cards.scryfall.io/normal/front/2/c/2c6865c1-8144-4b06-a599-a62382fe1124.jpg?1783920822"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.events.SpellCastPredicate
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val Poxwalkers = card("Poxwalkers") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Zombie"
    power = 3
    toughness = 1
    oracleText = "Deathtouch\nCurse of the Walking Pox — Whenever you cast a spell from anywhere other than your hand, return this card from your graveyard to the battlefield tapped."

    keywords(Keyword.DEATHTOUCH)

    triggeredAbility {
        trigger = Triggers.you.casts(
            requires = setOf(SpellCastPredicate.CastFromZoneOtherThan(Zone.HAND)),
        )
        triggerZone = Zone.GRAVEYARD
        effect = Effects.Move(
            EffectTarget.Self,
            Zone.BATTLEFIELD,
            placement = ZonePlacement.Tapped,
            fromZone = Zone.GRAVEYARD,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "49"
        artist = "Games Workshop"
        imageUri = "https://cards.scryfall.io/normal/front/c/a/ca79cd21-daaa-4a1c-bab5-cc4c783994ae.jpg?1783920914"
    }
}

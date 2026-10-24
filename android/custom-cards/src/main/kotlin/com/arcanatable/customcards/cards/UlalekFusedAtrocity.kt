package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.StackObjectKind

/**
 * Ulalek, Fused Atrocity — M3C #4. Exact definition from PR #60 (f159dea).
 *
 * Needs the Arcana engine patches for colorless-hybrid mana ({C/W}..{C/G}) and
 * CopyControlledStackObjects; it cannot load on runtime jars without them.
 */
val UlalekFusedAtrocity = card("Ulalek, Fused Atrocity") {
    manaCost = "{C/W}{C/U}{C/B}{C/R}{C/G}"
    colorIdentity = "WUBRG"
    typeLine = "Legendary Creature — Eldrazi"
    power = 2
    toughness = 5
    keywords(Keyword.DEVOID)
    oracleText = "Devoid (This card has no color.)\nWhenever you cast an Eldrazi spell, you may pay {C}{C}. If you do, copy all spells you control, then copy all other activated and triggered abilities you control. You may choose new targets for the copies. (Mana abilities can't be copied.)"
    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Any.withSubtype("Eldrazi"))
        effect = Effects.MayPay(ManaCost.parse("{C}{C}"), then =
            Effects.CopyControlledStackObjects(setOf(StackObjectKind.Spell)) then
                Effects.CopyControlledStackObjects(setOf(StackObjectKind.ActivatedAbility, StackObjectKind.TriggeredAbility))
        )
    }
    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "4"
        artist = "Alex Konstad"
        imageUri = "https://cards.scryfall.io/normal/front/f/d/fdad1b0e-d3cc-4d76-ae7e-fee12558cf2c.jpg?1783911439"
    }
}

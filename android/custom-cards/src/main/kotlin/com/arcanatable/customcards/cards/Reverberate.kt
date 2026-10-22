package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Copies a target spell on the stack, with the engine's optional copy-retarget decision. */
val Reverberate = card("Reverberate") {
    manaCost = "{R}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Copy target instant or sorcery spell. You may choose new targets for the copy."

    spell {
        val targetSpell = target(TargetFilter.InstantOrSorcerySpellOnStack)
        effect = Effects.CopyTargetSpell(targetSpell)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "207"
        artist = "David Astruga"
    }
}

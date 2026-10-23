package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val SisterOfSilence = card("Sister of Silence") {
    manaCost = "{4}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human Knight"
    power = 3
    toughness = 3
    oracleText = "Flash\nPsychic Abomination — When this creature enters, counter target instant spell, sorcery spell, activated ability, or triggered ability."
    keywords(Keyword.FLASH)

    triggeredAbility {
        trigger = Triggers.self.enters()
        target(TargetFilter.InstantSorcerySpellOrAbilityOnStack)
        effect = Effects.CounterSpellOrAbility()
    }

    metadata { rarity = Rarity.RARE; collectorNumber = "26"; artist = "Games Workshop" }
}

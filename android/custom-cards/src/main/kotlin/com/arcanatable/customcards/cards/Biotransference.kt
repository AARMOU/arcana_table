package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantCardType
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Biotransference — Warhammer 40,000 Commander #30 (Necron Dynasties). {2}{B}{B} Enchantment.
 *
 * Creatures you control are artifacts in addition to their other types. The same is true for creature
 * spells you control and creature cards you own that aren't on the battlefield.
 * Whenever you cast an artifact spell, you lose 1 life and create a 2/2 black Necron Warrior artifact
 * creature token.
 *
 * The first paragraph is one `GrantCardType("ARTIFACT")` over `Creature.youControl()` with both
 * cross-zone flags: the Encroaching Mycosynth shape narrowed from "nonland permanent" to "creature".
 * Off the battlefield only the filter's card predicate (creature) qualifies an object, and the reach is
 * the spells the enchantment's controller controls and the cards that player owns (hand, library,
 * graveyard, exile, command zone). Opponents' creatures, spells and cards are untouched.
 *
 * The cast trigger is `Triggers.you.casts(GameObjectFilter.Artifact)`, matched against the spell on the
 * stack with the grant applied. So every creature spell you cast triggers it, and so does any real
 * artifact spell. A noncreature, nonartifact spell doesn't.
 *
 * A card that leaves your graveyard is checked as it was there (CR 603.10a). That is what lets Imotekh
 * the Stormlord's "artifact cards leave your graveyard" see a creature card. Engine patch 0039 does that.
 *
 * Official ruling (2022-10-07): "Replacement effects that modify artifacts as they enter the battlefield
 * will apply after you apply Biotransference's effect." For example, if artifacts enter tapped, your
 * creatures enter tapped. That holds here because the grant reaches the card before it enters.
 */
val Biotransference = card("Biotransference") {
    manaCost = "{2}{B}{B}"
    colorIdentity = "B"
    typeLine = "Enchantment"
    oracleText = "Creatures you control are artifacts in addition to their other types. The same is true for " +
        "creature spells you control and creature cards you own that aren't on the battlefield.\n" +
        "Whenever you cast an artifact spell, you lose 1 life and create a 2/2 black Necron Warrior artifact " +
        "creature token."

    staticAbility {
        ability = GrantCardType(
            cardType = "ARTIFACT",
            filter = GroupFilter(GameObjectFilter.Creature.youControl()),
            includeControlledSpells = true,
            includeOwnedCardsOutsideBattlefield = true,
        )
    }

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Artifact)
        effect = Effects.LoseLife(1, EffectTarget.Controller) then Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Necron", "Warrior"),
            name = "Necron Warrior",
            artifactToken = true,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "30"
        artist = "Anthony Devine"
    }
}

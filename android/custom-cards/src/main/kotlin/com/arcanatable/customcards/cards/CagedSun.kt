package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalManaOnSourceTap
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TappedForManaType
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Caged Sun — {6} Artifact (Necron Dynasties; cache printing Commander Anthology Volume II #178).
 *
 * As this artifact enters, choose a color.
 * Creatures you control of the chosen color get +1/+1.
 * Whenever a land's ability causes you to add one or more mana of the chosen color, add an
 * additional one mana of that color.
 *
 * - The color is stored by `EntersWithChoice(ChoiceType.COLOR)` (a replacement, CR 614.12a), and both
 *   other lines read it off this artifact: the anthem through `withChosenColor()` (a multicolored
 *   creature that has the color counts), the mana line through `TappedForManaType.CHOSEN_COLOR`.
 * - The mana line is a triggered mana ability (CR 605.1b; official ruling 2022-06-10: "An ability that
 *   triggers from the resolution of an activated mana ability is also a mana ability… Caged Sun's
 *   triggered ability won't use the stack and can't be responded to"). `AdditionalManaOnSourceTap`
 *   on `GameObjectFilter.Land` with engine patch 0038:
 *   - `CHOSEN_COLOR` fires only when the land's ability added at least one mana of the chosen color
 *     (a dual tapped for the other color doesn't), and `color = null` then adds exactly one of it,
 *     even if the land made two or more;
 *   - `anyManaAbility = true`: "a land's ability", not "a land is tapped", so a land mana ability
 *     without {T} fires it too;
 *   - `onlyWhenYouAdd = true`: "causes **you** to add". The filter is any land, so a land you don't
 *     control whose mana you add (Piracy) counts, and an opponent's own mana never does.
 * - Nonland mana sources never fire it; two Caged Suns add one each; no color chosen, nothing.
 */
val CagedSun = card("Caged Sun") {
    manaCost = "{6}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "As this artifact enters, choose a color.\n" +
        "Creatures you control of the chosen color get +1/+1.\n" +
        "Whenever a land's ability causes you to add one or more mana of the chosen color, add an additional one mana of that color."

    replacementEffect(EntersWithChoice(ChoiceType.COLOR))

    staticAbility {
        ability = ModifyStats(
            powerBonus = 1,
            toughnessBonus = 1,
            filter = GroupFilter(GameObjectFilter.Creature.youControl().withChosenColor()),
        )
    }

    // Triggered mana ability (off the stack, CR 605.1b).
    staticAbility {
        ability = AdditionalManaOnSourceTap(
            sourceFilter = GameObjectFilter.Land,
            color = null,
            whenProducing = TappedForManaType.CHOSEN_COLOR,
            anyManaAbility = true,
            onlyWhenYouAdd = true,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "178"
        artist = "Scott Chou"
        imageUri = "https://cards.scryfall.io/normal/front/d/f/dfe6f9ec-3b7f-4c11-acd1-440e14217e5d.jpg?1783934714"
        ruling("2022-06-10", "An ability that triggers from the resolution of an activated mana ability is also a mana ability. This means that if the land's ability that caused you to add mana was an activated mana ability, Caged Sun's triggered ability won't use the stack and can't be responded to.")
    }
}

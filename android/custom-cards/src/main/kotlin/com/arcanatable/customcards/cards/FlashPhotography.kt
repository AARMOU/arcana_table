package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

/**
 * Flash Photography. Arcana's pinned engine has no "may cast as though it had flash if it targets
 * something you control" rule, so this plays at sorcery speed; the copy and flashback are exact.
 */
val FlashPhotography = card("Flash Photography") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "You may cast this spell as though it had flash if it targets a permanent you control.\n" +
        "Create a token that's a copy of target permanent.\nFlashback {4}{U}{U}"

    spell {
        val original = target(TargetFilter.Permanent)
        effect = Effects.CreateTokenCopyOfTarget(original)
    }

    keywordAbility(KeywordAbility.flashback("{4}{U}{U}"))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "463"
        artist = "Winona Nelson"
    }
}

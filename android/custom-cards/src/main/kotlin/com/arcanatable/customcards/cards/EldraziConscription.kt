package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.GrantTriggeredAbility
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Eldrazi Conscription — M3C #155.
 *
 * The printed annihilator keyword is numeric and is not itself executable in the pinned
 * SDK. Grant the *actual* attack trigger to the enchanted creature; it must use the
 * defending player (not the Aura's controller) and force the defending player to choose
 * which of their permanents to sacrifice.
 *
 * "Kindred" is part of the Aura's type line; it does NOT make the enchanted creature Eldrazi.
 */
val EldraziConscription = card("Eldrazi Conscription") {
    manaCost = "{8}"
    colorIdentity = ""
    typeLine = "Kindred Enchantment — Eldrazi Aura"
    oracleText = "Enchant creature\n" +
        "Enchanted creature gets +10/+10 and has trample and annihilator 2."

    auraTarget = TargetObject(filter = TargetFilter.Creature)
    staticAbility { ability = ModifyStats(10, 10) }
    staticAbility { ability = GrantKeyword(Keyword.TRAMPLE) }
    staticAbility {
        ability = GrantTriggeredAbility(
            grantedTriggeredAbility {
                trigger = Triggers.self.attacks()
                effect = Effects.Sacrifice(
                    GameObjectFilter.Permanent,
                    2,
                    EffectTarget.PlayerRef(Player.DefendingPlayer),
                )
                description = "Annihilator 2"
            },
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "155"
        artist = "Jaime Jones"
    }
}

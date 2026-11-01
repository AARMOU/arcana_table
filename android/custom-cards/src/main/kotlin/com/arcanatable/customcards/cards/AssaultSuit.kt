package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val AssaultSuit = card("Assault Suit") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +2/+2, has haste, can't attack you or planeswalkers you control, and can't be sacrificed.\nAt the beginning of each opponent's upkeep, you may have that player gain control of equipped creature until end of turn. If you do, untap it.\nEquip {3}"

    staticAbility { ability = ModifyStats(+2, +2, Filters.EquippedCreature) }
    staticAbility {
        ability = GrantKeyword(Keyword.HASTE, GroupFilter.attachedCreature())
    }
    staticAbility {
        ability = CantAttackYou(GroupFilter.attachedCreature(), coversPlaneswalkers = true)
    }
    staticAbility {
        ability = CantBeSacrificed(
            GroupFilter.attachedCreature(),
            CantBeSacrificed.Scope.ANY,
        )
    }

    triggeredAbility {
        trigger = Triggers.anOpponent.beginningOf(Step.UPKEEP)
        optional = true
        effect = Effects.IfYouDo(
            action = Effects.GiveControl(
                EffectTarget.EquippedCreature,
                EffectTarget.PlayerRef(Player.ActivePlayer),
                Duration.EndOfTurn,
            ),
            then = Effects.Untap(EffectTarget.EquippedCreature),
        )
    }

    equipAbility("{3}")

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "369"
        artist = "James Paick"
        imageUri = "https://cards.scryfall.io/normal/front/9/2/92b22076-b04e-4d65-9d9c-d3e4c7a3cf1c.jpg?1783915605"
    }
}

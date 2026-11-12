#!/usr/bin/env python3
"""#152: fail-closed source port of Eldrazi Temple's exact mana restriction.

Only for Argentum 7fb073337e4389cfabe2ebf4c6416633a7fc63d2 in a
disposable CI checkout. This does NOT build, commit, or replace app runtime JARs.
Each source anchor must match exactly once; never silently approximate a changed API.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) == 2 else None
if root is None or not (root / "mtg-sdk").is_dir() or not (root / "rules-engine").is_dir():
    raise SystemExit("Usage: port_to_upgraded.py <checked-out-argentum-engine>")

def replace_once(path: str, before: str, after: str) -> None:
    file = root / path
    text = file.read_text()
    if text.count(before) != 1 or after in text:
        raise SystemExit(f"FAIL CLOSED: unexpected SDK anchor or already patched: {path}: {before[:72]!r}")
    file.write_text(text.replace(before, after, 1))

def insert_after_unique_line(path: str, prefix: str, added: str) -> None:
    file = root / path
    lines = file.read_text().splitlines(keepends=True)
    positions = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(positions) != 1 or any(added.strip() in line for line in lines):
        raise SystemExit(f"FAIL CLOSED: missing/duplicate index anchor: {path}: {prefix}")
    lines.insert(positions[0] + 1, added)
    file.write_text("".join(lines))

replace_once(
    "mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/effects/ManaRestriction.kt",
    '''    @SerialName("ColorlessSpellsOnly")
    @Serializable
    data object ColorlessSpellsOnly : ManaRestriction {
        override val description: String = "Spend this mana only to cast colorless spells"
    }
''',
    '''    @SerialName("ColorlessSpellsOnly")
    @Serializable
    data object ColorlessSpellsOnly : ManaRestriction {
        override val description: String = "Spend this mana only to cast colorless spells"
    }

    /**
     * Restricted colorless mana for colorless subtype spells and activated abilities
     * of colorless sources of that subtype. Both checks are required.
     */
    @SerialName("ColorlessSubtypeSpellsOrAbilitiesOnly")
    @Serializable
    data class ColorlessSubtypeSpellsOrAbilitiesOnly(val subtype: String) : ManaRestriction {
        override val description: String =
            "Spend this mana only to cast colorless $subtype spells or activate abilities of colorless $subtype"
    }
''',
)
replace_once(
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/ManaPool.kt",
    '''    val abilitySourceCardTypes: Set<com.wingedsheep.sdk.core.CardType> = emptySet(),
    /** True when the source whose ability is being activated is a permanent on the battlefield. */''',
    '''    val abilitySourceCardTypes: Set<com.wingedsheep.sdk.core.CardType> = emptySet(),
    /** Source colorlessness is independent of the spell's colorlessness; fail closed. */
    val isAbilitySourceColorless: Boolean = false,
    /** True when the source whose ability is being activated is a permanent on the battlefield. */''',
)
replace_once(
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/ManaPool.kt",
    '''    is ManaRestriction.ColorlessSpellsOnly -> context.isSpellCast && context.isColorless
''',
    '''    is ManaRestriction.ColorlessSpellsOnly -> context.isSpellCast && context.isColorless
    is ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly ->
        context.subtypes.any { it.equals(subtype, ignoreCase = true) } &&
            ((context.isSpellCast && context.isColorless) ||
                (context.isAbilityActivation && context.isAbilitySourceColorless))
''',
)
replace_once(
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/AbilityPaymentContextBuilder.kt",
    '''    val subtypes = paymentSubtypesOf(cardComponent) + projected.getSubtypes(sourceId)
''',
    '''    // Projected battlefield types are authoritative; printed types are not additive
    // after a subtype-changing layer-4 effect.
    val isBattlefieldSource = sourceId in projected.getBaseState().getBattlefield()
    val subtypes = if (isBattlefieldSource) projected.getSubtypes(sourceId)
        else paymentSubtypesOf(cardComponent)
''',
)
replace_once(
    "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/AbilityPaymentContextBuilder.kt",
    '''        isAbilitySourceOnBattlefield = sourceId in projected.getBaseState().getBattlefield(),
''',
    '''        isAbilitySourceColorless = if (isBattlefieldSource) {
            projected.getColors(sourceId).isEmpty()
        } else {
            cardComponent.colors.isEmpty()
        },
        isAbilitySourceOnBattlefield = isBattlefieldSource,
''',
)
insert_after_unique_line(
    "docs/sdk-index.md",
    "- `ManaRestriction.ColorlessSpellsOnly`",
    '- `ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly(subtype: String)` — Restricted colorless mana for colorless subtype spells and activated abilities of colorless sources of that subtype.\n',
)
insert_after_unique_line(
    "mtg-sdk/src/test/resources/sdk-surface-baseline.txt",
    "ManaRestriction.ClassLevelUpOnly — ManaRestriction",
    "ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly — ManaRestriction — Eldrazi Temple — ManaRestriction.ColorlessSpellsOnly, ManaRestriction.SubtypeSpellsOrAbilitiesOnly — ColorlessSpellsOnly rejects every ability activation; SubtypeSpellsOrAbilitiesOnly permits colored Eldrazi abilities, so neither can constrain both subtype and colorlessness of a source while also admitting spells\n",
)
print("Applied exact Eldrazi Temple mana-source port to upgraded Argentum source; no runtime jars touched.")
